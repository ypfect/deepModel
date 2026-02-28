package com.deepmodel.relation.util;

import java.util.*;

/**
 * 排序结果: [1, b, 2, a, 3, 4, 5]
 */
public class KahnTopologicalSort {
    public List<String> topologicalSort(String[][] edges) {
        // 1. 数据准备
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Set<String> allNodes = new HashSet<>();

        // 初始化：确保所有出现在边里的节点都被记录
        for (String[] edge : edges) {
            String u = edge[0];
            String v = edge[1];
            adj.computeIfAbsent(u, k -> new ArrayList<>()).add(v);
            inDegree.put(v, inDegree.getOrDefault(v, 0) + 1);
            allNodes.add(u);
            allNodes.add(v);
        }

        // 2. 将所有入度为 0 的节点入队
        Queue<String> queue = new LinkedList<>();
        for (String node : allNodes) {
            if (inDegree.getOrDefault(node, 0) == 0) {
                queue.offer(node);
            }
        }

        List<String> result = new ArrayList<>();

        // 3. 核心循环
        while (!queue.isEmpty()) {
            String curr = queue.poll();
            result.add(curr);

            if (adj.containsKey(curr)) {
                for (String neighbor : adj.get(curr)) {
                    inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                    if (inDegree.get(neighbor) == 0) {
                        queue.offer(neighbor);
                    }
                }
            }
        }

        // 4. 环检测
        if (result.size() != allNodes.size()) {
            throw new IllegalStateException("检测到循环依赖！");
        }

        return result;
    }

    public static void main(String[] args) {
        KahnTopologicalSort KahnTopologicalSort = new KahnTopologicalSort();
        String[][] edges = {
            {"1", "2"}, {"1", "3"}, {"2", "4"}, {"3", "4"},
            {"4", "5"}, {"a", "3"}, {"b", "a"},
            {"x", "4"}, {"4", "o"}, {"o", "5"} // 新加入的边
        };

        try {
            List<String> result = KahnTopologicalSort
                .topologicalSort(edges);
            System.out.println("排序结果: " + result);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        }
    }
}