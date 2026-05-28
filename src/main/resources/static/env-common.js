/**
 * DeepModel 环境上下文：
 * - localStorage 持久化当前工作环境
 * - 自动为 /api/* 请求注入 X-Env header
 * - 提供 DM.mountEnvSelector(container, opts) 统一渲染环境选择器（原生 HTML / Vue 页面均可调用）
 */
(function (global) {
    const STORAGE_KEY = 'deepmodel.currentEnv';
    const COMPARE_KEY = 'deepmodel.compareEnv';

    function getEnv() {
        return localStorage.getItem(STORAGE_KEY) || '';
    }

    function setEnv(env) {
        if (env) {
            localStorage.setItem(STORAGE_KEY, env);
        } else {
            localStorage.removeItem(STORAGE_KEY);
        }
        global.dispatchEvent(new CustomEvent('dm-env-changed', { detail: { env: env || '' } }));
    }

    function getCompareEnv() {
        return localStorage.getItem(COMPARE_KEY) || '';
    }

    function setCompareEnv(env) {
        if (env) {
            localStorage.setItem(COMPARE_KEY, env);
        } else {
            localStorage.removeItem(COMPARE_KEY);
        }
        global.dispatchEvent(new CustomEvent('dm-compare-env-changed', { detail: { env: env || '' } }));
    }

    function headers(extra, overrideEnv) {
        const h = Object.assign({}, extra || {});
        const env = overrideEnv != null ? overrideEnv : getEnv();
        if (env) {
            h['X-Env'] = env;
        }
        return h;
    }

    /** 显式带 env 的 fetch（覆盖 localStorage 默认值） */
    async function dmFetchWithEnv(url, env, opts) {
        opts = opts || {};
        const merged = Object.assign({}, opts);
        merged.headers = headers(opts.headers || {}, env);
        return origFetch(url, merged);
    }

    let _envListCache = null;

    function isEnvInList(env, envs) {
        return !!(env && envs && envs.some(e => e.envName === env));
    }

    /** 若 localStorage 中保存的是已过滤掉的 global 环境，则清空。 */
    function sanitizeSavedEnv(envs) {
        const current = getEnv();
        if (current && !isEnvInList(current, envs)) {
            console.warn('[DM] 已保存的环境不可用（可能为 global 环境），已清空:', current);
            setEnv('');
        }
    }

    async function loadEnvList(forceRefresh) {
        if (_envListCache && !forceRefresh) return _envListCache;
        const res = await origFetch('/api/env/list');
        if (!res.ok) throw new Error('加载环境列表失败: HTTP ' + res.status);
        _envListCache = await res.json();
        sanitizeSavedEnv(_envListCache);
        return _envListCache;
    }

    async function loadApps(env) {
        if (!env) return [];
        const res = await origFetch('/api/env/apps?env=' + encodeURIComponent(env), {
            headers: { 'X-Env': env }
        });
        if (!res.ok) return [];
        return res.json();
    }

    function isEnvBlocked(e) {
        if (!e) return false;
        const s = String(e.envStatus || '').toLowerCase();
        return s === 'stopped' || s === 'start_err';
    }

    function isEnvRunnable(e) {
        return !isEnvBlocked(e);
    }

    function envStatusSuffix(e) {
        if (!e || isEnvRunnable(e)) return '';
        const s = String(e.envStatus || '').toLowerCase();
        if (s === 'stopped') return ' [已关闭]';
        if (s === 'start_err') return ' [启动失败]';
        return '';
    }

    function formatEnvLabel(e) {
        const zh = e.zhcnName ? ` (${e.zhcnName})` : '';
        const type = e.type ? ` [${e.type}]` : '';
        return `${e.envName}${zh}${type}${envStatusSuffix(e)}`;
    }

    function formatEnvOptionLabel(e) {
        return formatEnvLabel(e);
    }

    function buildEnvNotRunnableMessage(e) {
        const name = (e && e.envName) || '';
        const tag = envStatusSuffix(e).replace(/^\s*\[|\]$/g, '') || '不可用';
        return `环境「${name}」在运维平台标记为${tag}，请换其他环境，或在运维平台启动后再试`;
    }

    async function readApiError(res) {
        const text = await res.text();
        try {
            const j = JSON.parse(text);
            if (j.error) return j.error;
            if (j.message) return j.message;
        } catch (ignore) { /* not json */ }
        if (res.status === 503 || /503\s+Service Temporarily Unavailable/i.test(text)) {
            return '该环境服务已关闭或未启动（503），请选择其他运行中的环境';
        }
        if (/<title>503/i.test(text) || /<h1>503/i.test(text)) {
            return '该环境服务已关闭（nginx 503），请选择其他运行中的环境';
        }
        if (res.status === 502) {
            return '环境网关无响应（502），可能正在启动或已关闭';
        }
        return '请求失败: HTTP ' + res.status;
    }

    function envSearchHaystack(e) {
        return [e.envName, e.zhcnName, e.type, e.globalEnv]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();
    }

    function filterEnvRecords(envs, query) {
        const q = (query || '').trim().toLowerCase();
        if (!q) return envs;
        return envs.filter(e => envSearchHaystack(e).includes(q));
    }

    const ENV_THEMES = {
        light: {
            label: '#64748b',
            inputBg: '#fff',
            inputBorder: '#d1d5db',
            inputColor: '#1e293b',
            panelBg: '#fff',
            panelBorder: '#d1d5db',
            itemColor: '#1e293b',
            itemHover: '#f8fafc',
            itemSelectedBg: '#eff6ff',
            itemSelectedColor: '#1d4ed8',
            muted: '#94a3b8',
            shadow: '0 8px 24px rgba(0,0,0,0.08)'
        },
        dark: {
            label: '#8892a4',
            inputBg: '#0d1117',
            inputBorder: '#2a3347',
            inputColor: '#e2e8f0',
            panelBg: '#161b27',
            panelBorder: '#2a3347',
            itemColor: '#e2e8f0',
            itemHover: '#1e2a3d',
            itemSelectedBg: '#1e3a5c',
            itemSelectedColor: '#93c5fd',
            muted: '#8892a4',
            shadow: '0 8px 24px rgba(0,0,0,0.35)'
        }
    };

    /**
     * 统一的环境选择器（可输入模糊过滤 envName / 中文名 / type / globalEnv）。
     *
     * @param {HTMLElement|string} container 挂载容器（DOM 或选择器字符串）
     * @param {Object} [opts]
     * @param {string} [opts.label='环境']         前缀标签
     * @param {'global'|'compare'|string} [opts.scope='global']
     * @param {string} [opts.placeholder='-- 选择环境 --']
     * @param {string} [opts.width='260px']
     * @param {'light'|'dark'} [opts.theme='light']
     * @param {Function} [opts.onChange]           (env) => void
     * @returns {{getValue, setValue, refresh, inputEl, selectEl}}
     */
    function mountEnvSelector(container, opts) {
        opts = opts || {};
        const root = typeof container === 'string' ? document.querySelector(container) : container;
        if (!root) throw new Error('mountEnvSelector: container not found');

        const scope = opts.scope || 'global';
        const label = opts.label != null ? opts.label : (scope === 'compare' ? '比较环境' : '环境');
        const placeholder = opts.placeholder || '-- 选择环境 --';
        const width = opts.width || '260px';
        const theme = ENV_THEMES[opts.theme || 'light'] || ENV_THEMES.light;
        const inline = width !== '100%';

        const wrap = document.createElement('div');
        wrap.style.cssText = (inline ? 'display:inline-flex;' : 'display:flex;width:100%;')
            + 'align-items:center;gap:8px;font-size:13px;';
        const labelHtml = label
            ? `<span style="color:${theme.label};white-space:nowrap;">${label}</span>` : '';
        wrap.innerHTML = `
            ${labelHtml}
            <div class="dm-env-box" style="position:relative;${inline ? 'width:' + width + ';' : 'flex:1;min-width:0;'}">
                <input type="text" class="dm-env-input" autocomplete="off" spellcheck="false"
                    placeholder="${placeholder}"
                    style="width:100%;padding:7px 10px;border:1px solid ${theme.inputBorder};border-radius:6px;
                           background:${theme.inputBg};color:${theme.inputColor};font-size:13px;
                           outline:none;box-sizing:border-box;">
                <div class="dm-env-panel" style="display:none;position:absolute;z-index:3000;
                     top:calc(100% + 4px);left:0;right:0;max-height:320px;overflow:auto;
                     background:${theme.panelBg};border:1px solid ${theme.panelBorder};
                     border-radius:6px;box-shadow:${theme.shadow};">
                    <div class="dm-env-list"></div>
                </div>
            </div>
        `;
        root.appendChild(wrap);

        const input = wrap.querySelector('.dm-env-input');
        const panel = wrap.querySelector('.dm-env-panel');
        const list  = wrap.querySelector('.dm-env-list');

        let allEnvs = [];
        let selected = '';
        let panelOpen = false;
        let highlightIdx = 0;
        let suppressEmit = false;

        function readInitial() {
            if (scope === 'global') return getEnv();
            if (scope === 'compare') return getCompareEnv();
            return opts.initial || '';
        }
        function writeValue(v) {
            if (scope === 'global') setEnv(v);
            else if (scope === 'compare') setCompareEnv(v);
        }
        function findEnv(name) {
            return allEnvs.find(e => e.envName === name);
        }
        function syncInputDisplay() {
            if (panelOpen) return;
            const e = findEnv(selected);
            input.value = e ? formatEnvLabel(e) : '';
            input.placeholder = selected ? '' : placeholder;
        }
        function emitChange() {
            if (suppressEmit) return;
            writeValue(selected);
            if (typeof opts.onChange === 'function') {
                try { opts.onChange(selected); } catch (e) { console.error(e); }
            }
        }
        function renderList(query) {
            const items = filterEnvRecords(allEnvs, query);
            if (highlightIdx >= items.length) highlightIdx = Math.max(0, items.length - 1);
            if (items.length === 0) {
                list.innerHTML = `<div style="padding:10px 12px;color:${theme.muted};font-size:12px;text-align:center;">无匹配环境</div>`;
                return;
            }
            list.innerHTML = items.map((e, idx) => {
                const active = idx === highlightIdx;
                const picked = e.envName === selected;
                const bg = active ? theme.itemHover : (picked ? theme.itemSelectedBg : 'transparent');
                const color = picked ? theme.itemSelectedColor : theme.itemColor;
                const sub = [e.zhcnName, e.type, e.globalEnv].filter(Boolean).join(' · ');
                const nameStyle = isEnvRunnable(e) ? '' : 'color:#fca5a5;';
                return `<div class="dm-env-item" data-env="${e.envName}" data-idx="${idx}"
                    style="padding:8px 12px;cursor:pointer;color:${color};background:${bg};">
                    <div style="font-size:13px;line-height:1.4;${nameStyle}">${formatEnvLabel(e)}</div>
                    ${sub ? `<div style="font-size:11px;color:${theme.muted};margin-top:2px;">${sub}</div>` : ''}
                </div>`;
            }).join('');
            list.querySelectorAll('.dm-env-item').forEach(el => {
                el.addEventListener('mousedown', (ev) => {
                    ev.preventDefault();
                    selectEnv(el.getAttribute('data-env') || '');
                });
            });
        }
        function openPanel() {
            panelOpen = true;
            panel.style.display = 'block';
            highlightIdx = 0;
            renderList(input.value);
        }
        function closePanel() {
            panelOpen = false;
            panel.style.display = 'none';
            syncInputDisplay();
        }
        function selectEnv(envName) {
            selected = envName || '';
            closePanel();
            emitChange();
        }
        function resolveInputOnClose() {
            const q = input.value.trim();
            if (!q) {
                selectEnv('');
                return;
            }
            const qLower = q.toLowerCase();
            const exact = allEnvs.find(e =>
                e.envName === q ||
                e.envName.toLowerCase() === qLower ||
                formatEnvLabel(e).toLowerCase() === qLower
            );
            if (exact) {
                selectEnv(exact.envName);
                return;
            }
            const filtered = filterEnvRecords(allEnvs, q);
            if (filtered.length === 1) {
                selectEnv(filtered[0].envName);
                return;
            }
            closePanel();
        }

        input.addEventListener('focus', () => {
            openPanel();
            input.select();
        });
        input.addEventListener('input', () => {
            highlightIdx = 0;
            renderList(input.value);
        });
        input.addEventListener('keydown', (ev) => {
            const items = filterEnvRecords(allEnvs, input.value);
            if (ev.key === 'ArrowDown') {
                ev.preventDefault();
                if (!panelOpen) openPanel();
                highlightIdx = Math.min(highlightIdx + 1, Math.max(0, items.length - 1));
                renderList(input.value);
            } else if (ev.key === 'ArrowUp') {
                ev.preventDefault();
                highlightIdx = Math.max(highlightIdx - 1, 0);
                renderList(input.value);
            } else if (ev.key === 'Enter') {
                ev.preventDefault();
                if (items[highlightIdx]) selectEnv(items[highlightIdx].envName);
                else resolveInputOnClose();
            } else if (ev.key === 'Escape') {
                ev.preventDefault();
                closePanel();
                input.blur();
            }
        });
        input.addEventListener('blur', () => {
            setTimeout(() => {
                if (!panelOpen) return;
                resolveInputOnClose();
            }, 120);
        });
        document.addEventListener('click', (e) => {
            if (!wrap.contains(e.target)) {
                if (panelOpen) resolveInputOnClose();
                else closePanel();
            }
        });

        async function refresh() {
            allEnvs = await loadEnvList();
            const current = selected || readInitial();
            if (current && findEnv(current)) {
                selected = current;
            } else if (current) {
                selected = '';
            }
            syncInputDisplay();
            if (panelOpen) renderList(input.value);
        }

        if (scope === 'global') {
            global.addEventListener('dm-env-changed', (e) => {
                const v = (e.detail && e.detail.env) || '';
                if (selected !== v) {
                    selected = v;
                    syncInputDisplay();
                }
            });
        } else if (scope === 'compare') {
            global.addEventListener('dm-compare-env-changed', (e) => {
                const v = (e.detail && e.detail.env) || '';
                if (selected !== v) {
                    selected = v;
                    syncInputDisplay();
                }
            });
        }

        refresh().catch(err => console.error('加载环境列表失败', err));

        return {
            inputEl: input,
            selectEl: input,
            getValue: () => selected,
            setValue: (v) => {
                suppressEmit = true;
                selected = v || '';
                syncInputDisplay();
                suppressEmit = false;
                emitChange();
            },
            refresh
        };
    }

    /**
     * 填充已有 &lt;select&gt; 的环境选项（management 对比页等仍用原生 select 时）。
     */
    async function populateEnvSelect(selectEl, selectedEnv, placeholder) {
        if (!selectEl) return;
        const ph = placeholder || selectEl.options[0]?.text || '-- 选择环境 --';
        const envs = await loadEnvList();
        const current = selectedEnv || selectEl.value || '';
        selectEl.innerHTML = `<option value="">${ph}</option>`;
        envs.forEach(e => {
            const opt = document.createElement('option');
            opt.value = e.envName;
            const zh = e.zhcnName ? ` (${e.zhcnName})` : '';
            const type = e.type ? ` [${e.type}]` : '';
            opt.textContent = `${e.envName}${zh}${type}`;
            selectEl.appendChild(opt);
        });
        if (current) selectEl.value = current;
    }

    /**
     * 给已有 &lt;select&gt; 绑定 env 变更逻辑（不写 DOM，只绑事件）。
     */
    function bindEnvSelect(selectEl, opts) {
        if (!selectEl) throw new Error('bindEnvSelect: selectEl not found');
        opts = opts || {};
        const scope = opts.scope || 'global';

        function writeValue(v) {
            if (scope === 'global') setEnv(v);
            else if (scope === 'compare') setCompareEnv(v);
        }
        function emitChange() {
            writeValue(selectEl.value);
            if (typeof opts.onChange === 'function') {
                try { opts.onChange(selectEl.value); } catch (e) { console.error(e); }
            }
        }
        selectEl.addEventListener('change', emitChange);

        if (scope === 'global') {
            global.addEventListener('dm-env-changed', (e) => {
                const v = (e.detail && e.detail.env) || '';
                if (selectEl.value !== v) selectEl.value = v;
            });
        } else if (scope === 'compare') {
            global.addEventListener('dm-compare-env-changed', (e) => {
                const v = (e.detail && e.detail.env) || '';
                if (selectEl.value !== v) selectEl.value = v;
            });
        }

        return {
            selectEl,
            getValue: () => selectEl.value,
            setValue: (v) => { selectEl.value = v || ''; emitChange(); },
            refresh: () => populateEnvSelect(selectEl,
                scope === 'global' ? getEnv() : (scope === 'compare' ? getCompareEnv() : ''))
        };
    }

    /** 填充 datalist 的 appName 选项（用于 input list 联想） */
    async function fillAppDatalist(datalistEl, env) {
        if (!datalistEl) return;
        if (!env) { datalistEl.innerHTML = ''; return; }
        const apps = await loadApps(env);
        datalistEl.innerHTML = apps.map(a => `<option value="${a}">`).join('');
    }

    /**
     * 多选 appName 下拉（自定义实现，无第三方依赖）。
     *
     * @param {HTMLElement|string} container
     * @param {Object} opts
     * @param {string} [opts.placeholder='选择 App（多选）']
     * @param {string} [opts.width='100%']
     * @param {string[]} [opts.initial]  初始选中
     * @param {Function} [opts.onChange] (apps: string[]) => void
     * @returns {{getValues: Function, setValues: Function, refresh: Function}}
     */
    function mountAppMultiSelect(container, opts) {
        opts = opts || {};
        const root = typeof container === 'string' ? document.querySelector(container) : container;
        if (!root) throw new Error('mountAppMultiSelect: container not found');
        const placeholder = opts.placeholder || '选择 App（多选）';
        const width = opts.width || '100%';

        const wrap = document.createElement('div');
        wrap.style.cssText = `position:relative;width:${width};font-size:13px;`;
        wrap.innerHTML = `
            <div class="dm-app-trigger" style="
                min-height:34px;padding:5px 8px;
                border:1px solid #d1d5db;border-radius:6px;
                background:#fff;cursor:pointer;display:flex;
                align-items:center;flex-wrap:wrap;gap:4px;">
                <span class="dm-app-placeholder" style="color:#94a3b8;">${placeholder}</span>
            </div>
            <div class="dm-app-panel" style="
                display:none;position:absolute;z-index:1000;
                top:calc(100% + 4px);left:0;right:0;
                max-height:280px;overflow:auto;
                background:#fff;border:1px solid #d1d5db;
                border-radius:6px;box-shadow:0 8px 24px rgba(0,0,0,0.08);
                padding:6px 0;">
                <div class="dm-app-search" style="padding:6px 10px;border-bottom:1px solid #f1f5f9;">
                    <input type="text" placeholder="搜索 App..." style="
                        width:100% !important;padding:5px 8px !important;margin:0 !important;
                        display:block !important;
                        border:1px solid #e2e8f0 !important;border-radius:4px !important;
                        font-size:12px;outline:none;box-sizing:border-box;">
                </div>
                <div class="dm-app-actions" style="padding:4px 10px;display:flex;gap:8px;border-bottom:1px solid #f1f5f9;">
                    <a href="javascript:void(0)" class="dm-app-all" style="font-size:11px;color:#2563eb;">全选</a>
                    <a href="javascript:void(0)" class="dm-app-none" style="font-size:11px;color:#64748b;">清空</a>
                    <span class="dm-app-counter" style="margin-left:auto;font-size:11px;color:#94a3b8;"></span>
                </div>
                <div class="dm-app-list"></div>
            </div>
        `;
        root.appendChild(wrap);

        const trigger = wrap.querySelector('.dm-app-trigger');
        const panel   = wrap.querySelector('.dm-app-panel');
        const search  = wrap.querySelector('.dm-app-search input');
        const list    = wrap.querySelector('.dm-app-list');
        const counter = wrap.querySelector('.dm-app-counter');
        const allBtn  = wrap.querySelector('.dm-app-all');
        const noneBtn = wrap.querySelector('.dm-app-none');
        const placeholderEl = wrap.querySelector('.dm-app-placeholder');

        let allApps = [];
        let selected = new Set(opts.initial || []);

        function renderChips() {
            // 清空已渲染的 chip（保留 placeholder span）
            [...trigger.querySelectorAll('.dm-app-chip')].forEach(c => c.remove());
            if (selected.size === 0) {
                placeholderEl.style.display = '';
                return;
            }
            placeholderEl.style.display = 'none';
            [...selected].forEach(app => {
                const chip = document.createElement('span');
                chip.className = 'dm-app-chip';
                chip.style.cssText = `
                    display:inline-flex;align-items:center;gap:4px;
                    padding:2px 6px 2px 8px;border-radius:4px;
                    background:#eff6ff;color:#1d4ed8;font-size:12px;`;
                chip.innerHTML = `${app}<span class="dm-chip-x" style="cursor:pointer;font-size:14px;line-height:1;">×</span>`;
                chip.querySelector('.dm-chip-x').addEventListener('click', (e) => {
                    e.stopPropagation();
                    selected.delete(app);
                    renderChips();
                    renderList();
                    fireChange();
                });
                trigger.appendChild(chip);
            });
        }

        function renderList(filter) {
            const q = (filter || '').trim().toLowerCase();
            const items = q ? allApps.filter(a => a.toLowerCase().includes(q)) : allApps;
            list.innerHTML = items.map(app => {
                const checked = selected.has(app) ? 'checked' : '';
                return `<label style="
                    display:flex;align-items:center;gap:8px;
                    padding:5px 10px;cursor:pointer;font-size:13px;color:#1e293b;"
                    onmouseover="this.style.background='#f8fafc'"
                    onmouseout="this.style.background=''">
                    <input type="checkbox" data-app="${app}" ${checked}
                        style="width:auto !important;display:inline-block !important;margin:0 !important;padding:0 !important;border:0 !important;accent-color:#2563eb;cursor:pointer;">
                    <span>${app}</span>
                </label>`;
            }).join('') || '<div style="padding:10px;color:#94a3b8;font-size:12px;text-align:center;">无匹配项</div>';

            [...list.querySelectorAll('input[type=checkbox]')].forEach(cb => {
                cb.addEventListener('change', () => {
                    const app = cb.getAttribute('data-app');
                    if (cb.checked) selected.add(app); else selected.delete(app);
                    renderChips();
                    updateCounter();
                    fireChange();
                });
            });
            updateCounter();
        }

        function updateCounter() {
            counter.textContent = `已选 ${selected.size} / ${allApps.length}`;
        }

        function fireChange() {
            if (typeof opts.onChange === 'function') {
                try { opts.onChange([...selected]); } catch (e) { console.error(e); }
            }
        }

        trigger.addEventListener('click', () => {
            if (panel.style.display === 'none') {
                panel.style.display = 'block';
                search.value = '';
                renderList();
                search.focus();
            } else {
                panel.style.display = 'none';
            }
        });
        document.addEventListener('click', (e) => {
            if (!wrap.contains(e.target)) panel.style.display = 'none';
        });
        search.addEventListener('input', () => renderList(search.value));
        allBtn.addEventListener('click', () => {
            const q = search.value.trim().toLowerCase();
            const items = q ? allApps.filter(a => a.toLowerCase().includes(q)) : allApps;
            items.forEach(a => selected.add(a));
            renderChips(); renderList(search.value); fireChange();
        });
        noneBtn.addEventListener('click', () => {
            selected.clear();
            renderChips(); renderList(search.value); fireChange();
        });

        async function refresh(env) {
            allApps = env ? await loadApps(env) : [];
            // 已选项中如果不在新列表里就剔除
            selected = new Set([...selected].filter(a => allApps.includes(a)));
            renderChips();
            renderList(search.value);
        }

        renderChips();
        renderList();

        return {
            getValues: () => [...selected],
            setValues: (arr) => {
                selected = new Set(arr || []);
                renderChips();
                renderList(search.value);
            },
            refresh
        };
    }

    const origFetch = global.fetch.bind(global);

    // ====================== 环境快照预热 & 全页 Loading ======================
    let envReadyGateEnabled = true;
    let envOverlayEl = null;
    let envOverlayEnv = '';
    /** @type {'pick'|'loading'|null} */
    let envOverlayMode = null;
    let overlayEnvSelector = null;
    let currentWarmupEnv = '';
    const envReadyCache = new Set();
    const envWarmupInflight = new Map();
    const envWarmupAbort = new Map();

    function ensureEnvOverlay() {
        if (envOverlayEl) return envOverlayEl;
        envOverlayEl = document.createElement('div');
        envOverlayEl.id = 'dm-env-loading-overlay';
        envOverlayEl.setAttribute('role', 'alert');
        envOverlayEl.style.cssText = [
            'display:none', 'position:fixed', 'inset:0', 'z-index:99999',
            'background:rgba(15,23,42,0.55)', 'backdrop-filter:blur(2px)',
            'align-items:center', 'justify-content:center',
            'color:#f8fafc', 'font-family:system-ui,-apple-system,sans-serif',
            'padding:24px', 'box-sizing:border-box'
        ].join(';');
        envOverlayEl.innerHTML = `
            <div class="dm-env-panel-loading" style="display:none;flex-direction:column;align-items:center;gap:14px;max-width:440px;">
                <div style="width:44px;height:44px;border:3px solid rgba(255,255,255,0.25);
                            border-top-color:#60a5fa;border-radius:50%;animation:dmEnvSpin 0.9s linear infinite;"></div>
                <div class="dm-env-overlay-title" style="font-size:16px;font-weight:600;text-align:center;">正在加载环境元数据</div>
                <div class="dm-env-overlay-sub" style="font-size:13px;color:#cbd5e1;text-align:center;line-height:1.5;">
                    首次加载约需 10～30 秒，请稍候…
                </div>
                <button type="button" class="dm-env-switch-btn" style="
                    margin-top:4px;padding:8px 18px;border:1px solid rgba(148,163,184,0.6);
                    border-radius:6px;background:transparent;color:#e2e8f0;font-size:13px;cursor:pointer;">
                    切换其他环境
                </button>
            </div>
            <div class="dm-env-panel-pick" style="display:none;width:min(420px,92vw);
                        background:#1e293b;border:1px solid #334155;border-radius:10px;
                        padding:22px 20px;box-shadow:0 16px 40px rgba(0,0,0,0.35);">
                <div class="dm-env-pick-title" style="font-size:17px;font-weight:600;margin-bottom:6px;">请先选择工作环境</div>
                <div class="dm-env-pick-sub" style="font-size:13px;color:#94a3b8;margin-bottom:16px;line-height:1.5;">
                    选择后将自动加载该环境的元数据；仅「已关闭/启动失败」会标注并拦截
                </div>
                <div class="dm-env-pick-error" style="display:none;margin-bottom:12px;padding:10px 12px;
                     background:#450a0a;border:1px solid #7f1d1d;border-radius:6px;color:#fecaca;
                     font-size:13px;line-height:1.5;"></div>
                <div id="dm-env-overlay-pick-mount"></div>
            </div>
        `;
        if (!document.getElementById('dm-env-spin-style')) {
            const style = document.createElement('style');
            style.id = 'dm-env-spin-style';
            style.textContent = '@keyframes dmEnvSpin{to{transform:rotate(360deg)}}';
            document.head.appendChild(style);
        }
        envOverlayEl.querySelector('.dm-env-switch-btn').addEventListener('click', () => {
            if (envOverlayEnv) cancelEnvWarmup(envOverlayEnv);
            showEnvPickOverlay();
        });
        document.body.appendChild(envOverlayEl);
        return envOverlayEl;
    }

    function ensureOverlayEnvSelector() {
        const mount = document.getElementById('dm-env-overlay-pick-mount');
        if (!mount) return null;
        if (!overlayEnvSelector) {
            overlayEnvSelector = mountEnvSelector(mount, {
                label: '',
                width: '100%',
                theme: 'dark',
                placeholder: '输入环境名搜索…'
            });
        }
        return overlayEnvSelector;
    }

    function setOverlayPanels(mode) {
        const el = ensureEnvOverlay();
        const loading = el.querySelector('.dm-env-panel-loading');
        const pick = el.querySelector('.dm-env-panel-pick');
        envOverlayMode = mode;
        if (mode === 'loading') {
            loading.style.display = 'flex';
            pick.style.display = 'none';
            el.setAttribute('aria-busy', 'true');
        } else if (mode === 'pick') {
            loading.style.display = 'none';
            pick.style.display = 'block';
            el.removeAttribute('aria-busy');
        }
    }

    function openEnvOverlay() {
        ensureEnvOverlay();
        envOverlayEl.style.display = 'flex';
        document.body.style.overflow = 'hidden';
    }

    function closeEnvOverlay() {
        if (!envOverlayEl) return;
        envOverlayEl.style.display = 'none';
        document.body.style.overflow = '';
        envOverlayEnv = '';
        envOverlayMode = null;
        const loading = envOverlayEl.querySelector('.dm-env-panel-loading');
        const pick = envOverlayEl.querySelector('.dm-env-panel-pick');
        if (loading) loading.style.display = 'none';
        if (pick) pick.style.display = 'none';
    }

    function setEnvPickError(message) {
        const el = ensureEnvOverlay().querySelector('.dm-env-pick-error');
        if (!el) return;
        if (message) {
            el.textContent = message;
            el.style.display = 'block';
        } else {
            el.textContent = '';
            el.style.display = 'none';
        }
    }

    function showEnvPickOverlay(errorMessage) {
        openEnvOverlay();
        setOverlayPanels('pick');
        setEnvPickError(errorMessage || '');
        const sel = ensureOverlayEnvSelector();
        if (sel && typeof sel.refresh === 'function') {
            sel.refresh().catch(err => console.error('[DM] 刷新环境列表失败', err));
        }
        const input = sel && sel.inputEl;
        if (input) {
            setTimeout(() => input.focus(), 80);
        }
    }

    function showEnvLoadingOverlay(env) {
        const el = ensureEnvOverlay();
        envOverlayEnv = env || '';
        const title = el.querySelector('.dm-env-overlay-title');
        const sub = el.querySelector('.dm-env-overlay-sub');
        if (title) {
            title.textContent = env
                ? `正在加载环境「${env}」元数据`
                : '正在加载环境元数据';
        }
        if (sub) {
            sub.textContent = '加载中可点击下方按钮切换其他环境';
        }
        openEnvOverlay();
        setOverlayPanels('loading');
        global.dispatchEvent(new CustomEvent('dm-env-loading', { detail: { env: env || '' } }));
    }

    function cancelEnvWarmup(env) {
        if (!env) return;
        const ac = envWarmupAbort.get(env);
        if (ac) {
            ac.abort();
            envWarmupAbort.delete(env);
        }
        envWarmupInflight.delete(env);
        if (currentWarmupEnv === env) currentWarmupEnv = '';
    }

    function isEnvReadyCached(env) {
        return env && envReadyCache.has(env);
    }

    async function fetchSnapshotStatus(env) {
        const res = await origFetch(
            '/api/env/snapshot/status?env=' + encodeURIComponent(env),
            { headers: { 'X-Env': env } }
        );
        if (!res.ok) {
            throw new Error('查询快照状态失败: HTTP ' + res.status);
        }
        return res.json();
    }

    async function warmupEnv(env) {
        if (!env) return false;
        if (isEnvReadyCached(env)) {
            closeEnvOverlay();
            return true;
        }
        if (currentWarmupEnv && currentWarmupEnv !== env) {
            cancelEnvWarmup(currentWarmupEnv);
        }
        if (envWarmupInflight.has(env)) {
            return envWarmupInflight.get(env);
        }
        const task = (async () => {
            const ac = new AbortController();
            envWarmupAbort.set(env, ac);
            currentWarmupEnv = env;
            let aborted = false;
            try {
                const status = await fetchSnapshotStatus(env);
                if (ac.signal.aborted) {
                    aborted = true;
                    return false;
                }
                if (status && status.loaded) {
                    envReadyCache.add(env);
                    global.dispatchEvent(new CustomEvent('dm-env-ready', { detail: { env } }));
                    return true;
                }
                showEnvLoadingOverlay(env);
                const res = await origFetch('/api/env/snapshot/warmup', {
                    method: 'POST',
                    headers: headers({}, env),
                    signal: ac.signal
                });
                if (ac.signal.aborted) {
                    aborted = true;
                    return false;
                }
                if (!res.ok) {
                    throw new Error(await readApiError(res));
                }
                const data = await res.json();
                if (data && data.env) envReadyCache.add(data.env);
                else envReadyCache.add(env);
                global.dispatchEvent(new CustomEvent('dm-env-ready', { detail: { env } }));
                return true;
            } catch (err) {
                if (err && err.name === 'AbortError') {
                    aborted = true;
                    return false;
                }
                console.error('[DM] 环境预热失败', env, err);
                const msg = err && err.message ? err.message : String(err);
                global.dispatchEvent(new CustomEvent('dm-env-ready-error', {
                    detail: { env, message: msg }
                }));
                if (envOverlayMode === 'loading' && getEnv() === env) {
                    showEnvPickOverlay(msg);
                }
                return false;
            } finally {
                envWarmupAbort.delete(env);
                envWarmupInflight.delete(env);
                if (currentWarmupEnv === env) currentWarmupEnv = '';
                if (!aborted && envOverlayMode === 'loading' && envOverlayEnv === env) {
                    closeEnvOverlay();
                }
            }
        })();
        envWarmupInflight.set(env, task);
        return task;
    }

    async function ensureEnvReady(env) {
        env = env || getEnv();
        if (!env) return false;
        const envs = _envListCache || await loadEnvList();
        const meta = envs.find(e => e.envName === env);
        if (meta && !isEnvRunnable(meta)) {
            const msg = buildEnvNotRunnableMessage(meta);
            showEnvPickOverlay(msg);
            global.dispatchEvent(new CustomEvent('dm-env-ready-error', { detail: { env, message: msg } }));
            return false;
        }
        if (isEnvReadyCached(env)) return true;
        return warmupEnv(env);
    }

    function whenEnvReady(env) {
        return ensureEnvReady(env || getEnv());
    }

    function setEnvReadyGateEnabled(enabled) {
        envReadyGateEnabled = !!enabled;
    }

    function invalidateEnvReady(env) {
        if (env) envReadyCache.delete(env);
        else envReadyCache.clear();
    }

    async function bootEnvReadyGate() {
        if (!envReadyGateEnabled) return;
        if (document.body && document.body.hasAttribute('data-dm-skip-env-gate')) return;
        try {
            await loadEnvList();
        } catch (err) {
            console.error('[DM] 加载环境列表失败', err);
        }
        global.addEventListener('dm-env-changed', (e) => {
            if (!envReadyGateEnabled) return;
            const env = (e.detail && e.detail.env) || '';
            if (!env) {
                if (currentWarmupEnv) cancelEnvWarmup(currentWarmupEnv);
                showEnvPickOverlay();
                return;
            }
            if (envOverlayMode === 'pick') {
                closeEnvOverlay();
            }
            ensureEnvReady(env);
        });
        const initial = getEnv();
        if (initial) {
            ensureEnvReady(initial);
        } else {
            showEnvPickOverlay();
        }
    }

    // 全局 fetch 补丁：所有 /api/* 请求自动带当前 localStorage 中的 X-Env
    // 不影响 /api/env/list（不依赖环境）。
    global.fetch = function (url, opts) {
        opts = opts || {};
        if (typeof url === 'string' && url.startsWith('/api/') && !url.startsWith('/api/env/list')) {
            const existing = opts.headers || {};
            // 已显式带 X-Env 的请求不覆盖
            const hasEnvHeader =
                (existing instanceof Headers && existing.has && existing.has('X-Env')) ||
                (existing && (existing['X-Env'] || existing['x-env']));
            if (!hasEnvHeader) {
                opts.headers = headers(existing);
            }
        }
        return origFetch(url, opts);
    };

    global.DM = {
        getEnv, setEnv,
        getCompareEnv, setCompareEnv,
        headers,
        fetch: (url, o) => global.fetch(url, o),
        fetchWithEnv: dmFetchWithEnv,
        loadEnvList, loadApps,
        mountEnvSelector,
        populateEnvSelect,
        bindEnvSelect,
        fillAppDatalist,
        mountAppMultiSelect,
        ensureEnvReady,
        whenEnvReady,
        warmupEnv,
        setEnvReadyGateEnabled,
        invalidateEnvReady,
        isEnvReadyCached,
        showEnvPickOverlay,
        cancelEnvWarmup,
        isEnvRunnable,
        formatEnvOptionLabel,
        buildEnvNotRunnableMessage,
        readApiError
    };

    function scheduleEnvReadyGateBoot() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => { bootEnvReadyGate(); });
        } else {
            bootEnvReadyGate();
        }
    }
    scheduleEnvReadyGateBoot();
})(window);
