-- ============================================
-- 自动生成的SQL脚本
-- 来源: invoice_item_origin_amount_impact.xlsx
-- 说明: 回写字段和trigger字段都生成UPDATE语句
-- ============================================


-- ============================================
-- 对象: ArContract
-- ============================================

-- 回写字段 (5个)


-- ============================================
-- 对象: ArContractSubjectMatterItem
-- ============================================

-- 回写字段 (9个)


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originExecuteInvoiceAmountDir
UPDATE ar_contract_subject_matter_item
SET origin_execute_invoice_amount_dir = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originExecuteAppInvoiceAmountDir
UPDATE ar_contract_subject_matter_item
SET origin_execute_app_invoice_amount_dir = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originExecuteInvoiceAmountFrame
UPDATE ar_contract_subject_matter_item
SET origin_execute_invoice_amount_frame = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originInAcAmountFrame
UPDATE ar_contract_subject_matter_item
SET origin_in_ac_amount_frame = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originInAcAmountDir
UPDATE ar_contract_subject_matter_item
SET origin_in_ac_amount_dir = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originExecuteAppInvoiceAmountFrame
UPDATE ar_contract_subject_matter_item
SET origin_execute_app_invoice_amount_frame = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> ArContractSubjectMatterItem.originExecuteAppInvoiceAmountDirForValid
UPDATE ar_contract_subject_matter_item
SET origin_execute_app_invoice_amount_dir_for_valid = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> InvoiceApplicationItem.originMakeInvoiceAmount
UPDATE invoice_application_item
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceApplicationItem.originMakeInvoiceAmount -> ArContractSubjectMatterItem.originInvoiceMakeAppAmountFrame
UPDATE ar_contract_subject_matter_item
SET origin_invoice_make_app_amount_frame = (
    SELECT COALESCE(SUM(origin_make_invoice_amount), 0)
    FROM invoice_application_item
    WHERE invoice_application_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_application_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_application_item
    WHERE invoice_application_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_application_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> InvoiceApplicationItem.originMakeInvoiceAmount
UPDATE invoice_application_item
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceApplicationItem.originMakeInvoiceAmount -> ArContractSubjectMatterItem.originInvoiceMakeAppAmountDir
UPDATE ar_contract_subject_matter_item
SET origin_invoice_make_app_amount_dir = (
    SELECT COALESCE(SUM(origin_make_invoice_amount), 0)
    FROM invoice_application_item
    WHERE invoice_application_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_application_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_application_item
    WHERE invoice_application_item.ar_contract_subject_matter_item_id = ar_contract_subject_matter_item.id
        AND invoice_application_item.is_deleted = false
);


-- Trigger字段 (11个) - 使用UPDATE语句计算


-- Trigger字段: originExecuteInvoiceAmount (非期初执行开票原币金额)
-- 公式: origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_execute_invoice_amount = (origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame)
WHERE origin_execute_invoice_amount IS NULL OR origin_execute_invoice_amount != (origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame);


-- Trigger字段: invoiceStatusId (开票状态)
-- 公式: CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 'f' and abs(abs(origin_amount)) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.part' ELSE 'InvoiceStatusEnum.none' END
UPDATE ar_contract_subject_matter_item
SET invoice_status_id = (CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 'f' and abs(abs(origin_amount)) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.part' ELSE 'InvoiceStatusEnum.none' END)
WHERE invoice_status_id IS NULL OR invoice_status_id != (CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 'f' and abs(abs(origin_amount)) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_amount_dir + origin_init_invoice_amount_frame + origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame ) ) THEN 'InvoiceStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) = 0 THEN 'InvoiceStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_init_quantity_dir + invoice_init_quantity_frame + invoice_execute_quantity_dir + invoice_execute_quantity_frame ) ) THEN 'InvoiceStatusEnum.part' ELSE 'InvoiceStatusEnum.none' END);


-- Trigger字段: originInvoiceAmount (开票原币金额)
-- 公式: origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_invoice_amount = (origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame)
WHERE origin_invoice_amount IS NULL OR origin_invoice_amount != (origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame);


-- Trigger字段: originInvoiceEffAmount (有效开票原币金额)
-- 公式: (origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame)
UPDATE ar_contract_subject_matter_item
SET origin_invoice_eff_amount = ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame))
WHERE origin_invoice_eff_amount IS NULL OR origin_invoice_eff_amount != ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame));


-- Trigger字段: originOpenInvoiceAmount (可开票原币金额)
-- 公式: case when origin_amount=0 then 0 else (origin_amount - ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame))) end
UPDATE ar_contract_subject_matter_item
SET origin_open_invoice_amount = (case when origin_amount=0 then 0 else (origin_amount - ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame))) end)
WHERE origin_open_invoice_amount IS NULL OR origin_open_invoice_amount != (case when origin_amount=0 then 0 else (origin_amount - ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame))) end);


-- Trigger字段: originInvoiceEffAmountForValid (有效开票原币金额为了校验)
-- 公式: (origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir_for_valid + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame)
UPDATE ar_contract_subject_matter_item
SET origin_invoice_eff_amount_for_valid = ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir_for_valid + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame))
WHERE origin_invoice_eff_amount_for_valid IS NULL OR origin_invoice_eff_amount_for_valid != ((origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)-(origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir_for_valid + origin_execute_app_invoice_amount_frame)+(origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame));


-- Trigger字段: originAppInvoiceAmount (开票申请已开票原币金额)
-- 公式: origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_app_invoice_amount = (origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame)
WHERE origin_app_invoice_amount IS NULL OR origin_app_invoice_amount != (origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_amount_dir + origin_execute_app_invoice_amount_frame);


-- Trigger字段: originInAcAmount (发票立账发票原币金额)
-- 公式: origin_in_ac_amount_dir + origin_in_ac_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_in_ac_amount = (origin_in_ac_amount_dir + origin_in_ac_amount_frame)
WHERE origin_in_ac_amount IS NULL OR origin_in_ac_amount != (origin_in_ac_amount_dir + origin_in_ac_amount_frame);


-- Trigger字段: originInvoiceMakeAppAmount (原币开票申请金额(已关闭))
-- 公式: origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_invoice_make_app_amount = (origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)
WHERE origin_invoice_make_app_amount IS NULL OR origin_invoice_make_app_amount != (origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame);


-- Trigger字段: originInvoiceAppAmount (开票申请原币金额)
-- 公式: origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame
UPDATE ar_contract_subject_matter_item
SET origin_invoice_app_amount = (origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame)
WHERE origin_invoice_app_amount IS NULL OR origin_invoice_app_amount != (origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame);


-- Trigger字段: invoiceAppStatusId (开票申请状态)
-- 公式: CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_app_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.part' ELSE 'InvoiceAppStatusEnum.none' END
UPDATE ar_contract_subject_matter_item
SET invoice_app_status_id = (CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_app_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.part' ELSE 'InvoiceAppStatusEnum.none' END)
WHERE invoice_app_status_id IS NULL OR invoice_app_status_id != (CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) THEN invoice_app_status_id_str WHEN is_free_gift = 'f' and ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 'f' and abs(origin_amount) * ( 1 - ( SELECT invoice_app_amount_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_app_amount_dir + origin_invoice_not_make_app_amount_frame + origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame ) ) THEN 'InvoiceAppStatusEnum.part' WHEN is_free_gift = 't' and ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) = 0 THEN 'InvoiceAppStatusEnum.none' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) <= abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.all' WHEN is_free_gift = 't' and abs(quantity) * ( 1 - ( SELECT invoice_app_quantity_more_or_less_rate FROM contract_ar_contract WHERE contract_ar_contract_subject_matter_item.ar_contract_id = contract_ar_contract.ID ) ) > abs( ( invoice_not_make_app_quantity_dir + invoice_not_make_app_quantity_frame + invoice_make_app_quantity_dir + invoice_make_app_quantity_frame + invoice_init_app_quantity_dir + invoice_init_app_quantity_frame ) ) THEN 'InvoiceAppStatusEnum.part' ELSE 'InvoiceAppStatusEnum.none' END);


-- ============================================
-- 对象: InstallmentRcPlanItem
-- ============================================

-- 回写字段 (1个)


-- ============================================
-- 对象: Invoice
-- ============================================

-- 回写字段 (13个)


-- 回写: InvoiceItem.originAmount -> Invoice.originAmount
UPDATE invoice
SET origin_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> Invoice.originRedAmount
UPDATE invoice
SET origin_red_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> Invoice.originRedEffAmount
UPDATE invoice
SET origin_red_eff_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.receiptStatusId -> Invoice.receiptStatusId
UPDATE invoice
SET receipt_status_id = (
    SELECT COALESCE(SUM(receipt_status_id), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.invoiceReceiptStatusId -> Invoice.invoiceReceiptStatusId
UPDATE invoice
SET invoice_receipt_status_id = (
    SELECT COALESCE(SUM(invoice_receipt_status_id), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originNoRequiredReceiptAmount -> Invoice.originNoRequiredReceiptAmount
UPDATE invoice
SET origin_no_required_receipt_amount = (
    SELECT COALESCE(SUM(origin_no_required_receipt_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originRealDoDeclaredAmount -> Invoice.originRealDoDeclaredAmount
UPDATE invoice
SET origin_real_do_declared_amount = (
    SELECT COALESCE(SUM(origin_real_do_declared_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originNotReceiptAmount -> Invoice.originNotReceiptAmount
UPDATE invoice
SET origin_not_receipt_amount = (
    SELECT COALESCE(SUM(origin_not_receipt_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originNotRcfAmount -> Invoice.originNotRcfAmount
UPDATE invoice
SET origin_not_rcf_amount = (
    SELECT COALESCE(SUM(origin_not_rcf_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originInvoiceDoDeclaredAmount -> Invoice.originInvoiceDoDeclaredAmount
UPDATE invoice
SET origin_invoice_do_declared_amount = (
    SELECT COALESCE(SUM(origin_invoice_do_declared_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originRcfAmount -> Invoice.originRcfAmount
UPDATE invoice
SET origin_rcf_amount = (
    SELECT COALESCE(SUM(origin_rcf_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originDoDeclaredAmount -> Invoice.originDoDeclaredAmount
UPDATE invoice
SET origin_do_declared_amount = (
    SELECT COALESCE(SUM(origin_do_declared_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originReceiptAmount -> Invoice.originReceiptAmount
UPDATE invoice
SET origin_receipt_amount = (
    SELECT COALESCE(SUM(origin_receipt_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_id = invoice.id
        AND invoice_item.is_deleted = false
);


-- Trigger字段 (2个) - 使用UPDATE语句计算


-- Trigger字段: originNotRedAmount (原币未红冲金额)
-- 公式: origin_amount-origin_red_amount
UPDATE invoice
SET origin_not_red_amount = (origin_amount-origin_red_amount)
WHERE origin_not_red_amount IS NULL OR origin_not_red_amount != (origin_amount-origin_red_amount);


-- Trigger字段: issuedInvoiceStatusId (开税票状态)
-- 公式: case when bill_status in ('BillStatus.effective','BillStatus.submitted', 'BillStatus.approving') then (case when coalesce((select abs(sum(arap_invoice_tax.amount)) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) >= abs(origin_amount) then 'IssuedInvoiceStatus.all' when coalesce((select sum(arap_invoice_tax.amount) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) =0 then 'IssuedInvoiceStatus.none' else 'IssuedInvoiceStatus.part' end) else  'IssuedInvoiceStatus.none' end
UPDATE invoice
SET issued_invoice_status_id = (case when bill_status in ('BillStatus.effective','BillStatus.submitted', 'BillStatus.approving') then (case when coalesce((select abs(sum(arap_invoice_tax.amount)) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) >= abs(origin_amount) then 'IssuedInvoiceStatus.all' when coalesce((select sum(arap_invoice_tax.amount) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) =0 then 'IssuedInvoiceStatus.none' else 'IssuedInvoiceStatus.part' end) else  'IssuedInvoiceStatus.none' end)
WHERE issued_invoice_status_id IS NULL OR issued_invoice_status_id != (case when bill_status in ('BillStatus.effective','BillStatus.submitted', 'BillStatus.approving') then (case when coalesce((select abs(sum(arap_invoice_tax.amount)) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) >= abs(origin_amount) then 'IssuedInvoiceStatus.all' when coalesce((select sum(arap_invoice_tax.amount) from arap_invoice_tax where arap_invoice.id=arap_invoice_tax.invoice_id and arap_invoice_tax.is_deleted=false),0) =0 then 'IssuedInvoiceStatus.none' else 'IssuedInvoiceStatus.part' end) else  'IssuedInvoiceStatus.none' end);


-- ============================================
-- 对象: InvoiceApplication
-- ============================================

-- 回写字段 (4个)


-- 回写: InvoiceItem.originAmount -> InvoiceApplication.originMakeInvoiceAmount
UPDATE invoice_application
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_id = invoice_application.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_id = invoice_application.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> InvoiceApplicationItem.originMakeInvoiceAmount
UPDATE invoice_application_item
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceApplicationItem.originMakeInvoiceAmount -> InvoiceApplication.redRcfAmount
UPDATE invoice_application
SET red_rcf_amount = (
    SELECT COALESCE(SUM(origin_make_invoice_amount), 0)
    FROM invoice_application_item
    WHERE invoice_application_item.invoice_application_id = invoice_application.id
        AND invoice_application_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_application_item
    WHERE invoice_application_item.invoice_application_id = invoice_application.id
        AND invoice_application_item.is_deleted = false
);


-- ============================================
-- 对象: InvoiceApplicationItem
-- ============================================

-- 回写字段 (1个)


-- 回写: InvoiceItem.originAmount -> InvoiceApplicationItem.originMakeInvoiceAmount
UPDATE invoice_application_item
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
);


-- Trigger字段 (3个) - 使用UPDATE语句计算


-- Trigger字段: originEffectiveAppAmount (原币有效开票申请金额)
-- 公式: case when (closed_status = 'ClosedStatus.closed' or closed_status = 'ClosedStatus.opening') then coalesce(origin_make_invoice_amount,0) else coalesce(origin_amount,0) end
UPDATE invoice_application_item
SET origin_effective_app_amount = (case when (closed_status = 'ClosedStatus.closed' or closed_status = 'ClosedStatus.opening') then coalesce(origin_make_invoice_amount,0) else coalesce(origin_amount,0) end)
WHERE origin_effective_app_amount IS NULL OR origin_effective_app_amount != (case when (closed_status = 'ClosedStatus.closed' or closed_status = 'ClosedStatus.opening') then coalesce(origin_make_invoice_amount,0) else coalesce(origin_amount,0) end);


-- Trigger字段: originOpenInvoiceAmount (原币未开票金额)
-- 公式: origin_amount - origin_make_invoice_amount
UPDATE invoice_application_item
SET origin_open_invoice_amount = (origin_amount - origin_make_invoice_amount)
WHERE origin_open_invoice_amount IS NULL OR origin_open_invoice_amount != (origin_amount - origin_make_invoice_amount);


-- Trigger字段: invoiceStatusId (开票状态)
-- 公式: CASE WHEN (product_standard_type_id IS NOT NULL AND product_standard_type_id='ProductStandardType.quantity') OR is_free_gift THEN (CASE WHEN ABS (make_invoice_quantity)=0 THEN 'InvoiceStatus.none' WHEN ABS (make_invoice_quantity)>= ABS (quantity) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) ELSE (CASE WHEN ABS (origin_make_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN ABS (origin_make_invoice_amount)>= ABS (origin_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) END
UPDATE invoice_application_item
SET invoice_status_id = (CASE WHEN (product_standard_type_id IS NOT NULL AND product_standard_type_id='ProductStandardType.quantity') OR is_free_gift THEN (CASE WHEN ABS (make_invoice_quantity)=0 THEN 'InvoiceStatus.none' WHEN ABS (make_invoice_quantity)>= ABS (quantity) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) ELSE (CASE WHEN ABS (origin_make_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN ABS (origin_make_invoice_amount)>= ABS (origin_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) END)
WHERE invoice_status_id IS NULL OR invoice_status_id != (CASE WHEN (product_standard_type_id IS NOT NULL AND product_standard_type_id='ProductStandardType.quantity') OR is_free_gift THEN (CASE WHEN ABS (make_invoice_quantity)=0 THEN 'InvoiceStatus.none' WHEN ABS (make_invoice_quantity)>= ABS (quantity) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) ELSE (CASE WHEN ABS (origin_make_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN ABS (origin_make_invoice_amount)>= ABS (origin_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END) END);


-- ============================================
-- 对象: InvoiceItem
-- ============================================


-- Trigger字段 (11个) - 使用UPDATE语句计算


-- Trigger字段: originNotRedAmount (原币未红冲金额)
-- 公式: origin_amount-origin_red_amount
UPDATE invoice_item
SET origin_not_red_amount = (origin_amount-origin_red_amount)
WHERE origin_not_red_amount IS NULL OR origin_not_red_amount != (origin_amount-origin_red_amount);


-- Trigger字段: originRealInvoiceDoDeclaredAmount (原币发票实际未核销金额)
-- 公式: origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount 
UPDATE invoice_item
SET origin_real_invoice_do_declared_amount = (origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount )
WHERE origin_real_invoice_do_declared_amount IS NULL OR origin_real_invoice_do_declared_amount != (origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount );


-- Trigger字段: receiptStatusId (收款状态)
-- 公式: CASE WHEN is_free_gift THEN 'ReceiptStatus.all' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)=0 THEN 'ReceiptStatus.none' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)>=ABS (origin_amount) THEN 'ReceiptStatus.all' ELSE 'ReceiptStatus.part' END
UPDATE invoice_item
SET receipt_status_id = (CASE WHEN is_free_gift THEN 'ReceiptStatus.all' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)=0 THEN 'ReceiptStatus.none' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)>=ABS (origin_amount) THEN 'ReceiptStatus.all' ELSE 'ReceiptStatus.part' END)
WHERE receipt_status_id IS NULL OR receipt_status_id != (CASE WHEN is_free_gift THEN 'ReceiptStatus.all' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)=0 THEN 'ReceiptStatus.none' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount_include_jump+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END)>=ABS (origin_amount) THEN 'ReceiptStatus.all' ELSE 'ReceiptStatus.part' END);


-- Trigger字段: originNoRequiredReceiptAmount (原币无需收款金额)
-- 公式: CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END
UPDATE invoice_item
SET origin_no_required_receipt_amount = (CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)
WHERE origin_no_required_receipt_amount IS NULL OR origin_no_required_receipt_amount != (CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_dir_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END);


-- Trigger字段: originRealDoDeclaredAmount (原币实际未核销金额)
-- 公式: origin_amount - origin_done_declared_amount - origin_invoice_receipt_amount - ( origin_dir_receipt_amount - ( origin_done_declared_amount - origin_indir_declared_amount ) ) - origin_init_receipt_amount
UPDATE invoice_item
SET origin_real_do_declared_amount = (origin_amount - origin_done_declared_amount - origin_invoice_receipt_amount - ( origin_dir_receipt_amount - ( origin_done_declared_amount - origin_indir_declared_amount ) ) - origin_init_receipt_amount)
WHERE origin_real_do_declared_amount IS NULL OR origin_real_do_declared_amount != (origin_amount - origin_done_declared_amount - origin_invoice_receipt_amount - ( origin_dir_receipt_amount - ( origin_done_declared_amount - origin_indir_declared_amount ) ) - origin_init_receipt_amount);


-- Trigger字段: originNotReceiptAmount (原币未收款金额)
-- 公式: origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END) 
UPDATE invoice_item
SET origin_not_receipt_amount = (origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END) )
WHERE origin_not_receipt_amount IS NULL OR origin_not_receipt_amount != (origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END) );


-- Trigger字段: originNotRcfAmount (原币未收入确认金额)
-- 公式: origin_amount-(case when (abs(origin_init_rc_amount)+abs(origin_revenue_amount)+abs(origin_red_eff_amount))>abs(origin_amount) then origin_amount when (abs(origin_revenue_amount)+abs(origin_init_rc_amount)+abs(origin_red_eff_amount))>abs(origin_invoice_done_declared_amount+origin_init_rc_amount) then origin_revenue_amount+origin_init_rc_amount else origin_invoice_done_declared_amount+origin_init_rc_amount end)
UPDATE invoice_item
SET origin_not_rcf_amount = (origin_amount-(case when (abs(origin_init_rc_amount)+abs(origin_revenue_amount)+abs(origin_red_eff_amount))>abs(origin_amount) then origin_amount when (abs(origin_revenue_amount)+abs(origin_init_rc_amount)+abs(origin_red_eff_amount))>abs(origin_invoice_done_declared_amount+origin_init_rc_amount) then origin_revenue_amount+origin_init_rc_amount else origin_invoice_done_declared_amount+origin_init_rc_amount end))
WHERE origin_not_rcf_amount IS NULL OR origin_not_rcf_amount != (origin_amount-(case when (abs(origin_init_rc_amount)+abs(origin_revenue_amount)+abs(origin_red_eff_amount))>abs(origin_amount) then origin_amount when (abs(origin_revenue_amount)+abs(origin_init_rc_amount)+abs(origin_red_eff_amount))>abs(origin_invoice_done_declared_amount+origin_init_rc_amount) then origin_revenue_amount+origin_init_rc_amount else origin_invoice_done_declared_amount+origin_init_rc_amount end));


-- Trigger字段: originInvoiceDoDeclaredAmount (原币发票未核销金额)
-- 公式: origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount
UPDATE invoice_item
SET origin_invoice_do_declared_amount = (origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount)
WHERE origin_invoice_do_declared_amount IS NULL OR origin_invoice_do_declared_amount != (origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount);


-- Trigger字段: originRcfAmount (原币累计收入确认金额)
-- 公式: case when ( abs(origin_revenue_amount)+ abs(origin_init_rc_amount) + abs(origin_red_eff_amount) ) > abs(origin_amount) then origin_amount when ( abs(origin_init_rc_amount) + abs(origin_revenue_amount) + abs(origin_red_eff_amount) ) > abs( origin_invoice_done_declared_amount ) + abs(origin_init_rc_amount) then origin_revenue_amount + origin_init_rc_amount else origin_invoice_done_declared_amount + origin_init_rc_amount end
UPDATE invoice_item
SET origin_rcf_amount = (case when ( abs(origin_revenue_amount)+ abs(origin_init_rc_amount) + abs(origin_red_eff_amount) ) > abs(origin_amount) then origin_amount when ( abs(origin_init_rc_amount) + abs(origin_revenue_amount) + abs(origin_red_eff_amount) ) > abs( origin_invoice_done_declared_amount ) + abs(origin_init_rc_amount) then origin_revenue_amount + origin_init_rc_amount else origin_invoice_done_declared_amount + origin_init_rc_amount end)
WHERE origin_rcf_amount IS NULL OR origin_rcf_amount != (case when ( abs(origin_revenue_amount)+ abs(origin_init_rc_amount) + abs(origin_red_eff_amount) ) > abs(origin_amount) then origin_amount when ( abs(origin_init_rc_amount) + abs(origin_revenue_amount) + abs(origin_red_eff_amount) ) > abs( origin_invoice_done_declared_amount ) + abs(origin_init_rc_amount) then origin_revenue_amount + origin_init_rc_amount else origin_invoice_done_declared_amount + origin_init_rc_amount end);


-- Trigger字段: originDoDeclaredAmount (原币未核销金额)
-- 公式: origin_amount - origin_done_declared_amount-origin_init_receipt_amount -origin_invoice_receipt_amount
UPDATE invoice_item
SET origin_do_declared_amount = (origin_amount - origin_done_declared_amount-origin_init_receipt_amount -origin_invoice_receipt_amount)
WHERE origin_do_declared_amount IS NULL OR origin_do_declared_amount != (origin_amount - origin_done_declared_amount-origin_init_receipt_amount -origin_invoice_receipt_amount);


-- Trigger字段: originReceiptAmount (原币累计收款金额)
-- 公式: CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END
UPDATE invoice_item
SET origin_receipt_amount = (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END)
WHERE origin_receipt_amount IS NULL OR origin_receipt_amount != (CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (CASE WHEN (ABS (origin_dir_receipt_amount_include_jump)+ABS (origin_indir_declared_amount)+ABS (origin_invoice_receipt_amount)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_invoice_receipt_amount+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) ELSE (CASE WHEN (ABS (origin_invoice_receipt_amount)+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN ABS (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+ABS (origin_init_receipt_amount))> ABS (origin_amount) THEN origin_amount ELSE (origin_invoice_receipt_amount+(CASE WHEN ( SELECT COALESCE (assistant_accounting_method_id,'') FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID) !='' THEN (origin_dir_receipt_amount_include_jump) ELSE 0 END)+(CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) THEN ((origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invoice.ID)=TRUE THEN (origin_dir_receipt_amount_include_jump+origin_indir_declared_amount+origin_init_receipt_amount) ELSE (origin_invoice_receipt_amount+origin_init_receipt_amount) END))) ELSE origin_inv_verify_no_required_receipt_amount END)+origin_init_receipt_amount) END) END);


-- ============================================
-- 对象: RevenueConfirmation
-- ============================================

-- 回写字段 (8个)


-- 回写: InvoiceItem.originAmount -> RevenueConfirmation.originInvoiceAmount
UPDATE revenue_confirmation
SET origin_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> RevenueConfirmation.originInvoiceAppDoneAmount
UPDATE revenue_confirmation
SET origin_invoice_app_done_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> RevenueConfirmation.originInvoiceReserveAmount
UPDATE revenue_confirmation
SET origin_invoice_reserve_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_id = revenue_confirmation.id
        AND invoice_item.is_deleted = false
);


-- ============================================
-- 对象: RevenueConfirmationItem
-- ============================================

-- 回写字段 (5个)


-- 回写: InvoiceItem.originAmount -> RevenueConfirmationItem.originEffInvoiceAmount
UPDATE revenue_confirmation_item
SET origin_eff_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> RevenueConfirmationItem.originInvoiceAmount
UPDATE revenue_confirmation_item
SET origin_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> RevenueConfirmationItem.originInvoiceAppDoneAmount
UPDATE revenue_confirmation_item
SET origin_invoice_app_done_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> InvoiceApplicationItem.originMakeInvoiceAmount
UPDATE invoice_application_item
SET origin_make_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.invoice_application_item_id = invoice_application_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceApplicationItem.originMakeInvoiceAmount -> RevenueConfirmationItem.originInvoiceAppReserveAmount
UPDATE revenue_confirmation_item
SET origin_invoice_app_reserve_amount = (
    SELECT COALESCE(SUM(origin_make_invoice_amount), 0)
    FROM invoice_application_item
    WHERE invoice_application_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_application_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_application_item
    WHERE invoice_application_item.revenue_confirmation_item_id = revenue_confirmation_item.id
        AND invoice_application_item.is_deleted = false
);


-- Trigger字段 (7个) - 使用UPDATE语句计算


-- Trigger字段: originInvoiceReserveAmount (原币发票预占金额)
-- 公式: (origin_invoice_amount - origin_eff_invoice_amount) + origin_invoice_app_reserve_amount 
UPDATE revenue_confirmation_item
SET origin_invoice_reserve_amount = ((origin_invoice_amount - origin_eff_invoice_amount) + origin_invoice_app_reserve_amount )
WHERE origin_invoice_reserve_amount IS NULL OR origin_invoice_reserve_amount != ((origin_invoice_amount - origin_eff_invoice_amount) + origin_invoice_app_reserve_amount );


-- Trigger字段: originRealInvoiceDoDeclaredAmount (原币发票实际未核销金额)
-- 公式: origin_amount - origin_invoice_done_declared_amount - (origin_invoice_amount - origin_eff_invoice_amount) - origin_invoice_app_reserve_amount -origin_init_invoice_amount
UPDATE revenue_confirmation_item
SET origin_real_invoice_do_declared_amount = (origin_amount - origin_invoice_done_declared_amount - (origin_invoice_amount - origin_eff_invoice_amount) - origin_invoice_app_reserve_amount -origin_init_invoice_amount)
WHERE origin_real_invoice_do_declared_amount IS NULL OR origin_real_invoice_do_declared_amount != (origin_amount - origin_invoice_done_declared_amount - (origin_invoice_amount - origin_eff_invoice_amount) - origin_invoice_app_reserve_amount -origin_init_invoice_amount);


-- Trigger字段: invoiceStatusId (开票状态)
-- 公式: CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' or ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) ) THEN ( CASE WHEN origin_amount = 0 THEN 'InvoiceStatus.none' WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN quantity = 0 and init_invoice_qty=0 THEN 'InvoiceStatus.none' WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END
UPDATE revenue_confirmation_item
SET invoice_status_id = (CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' or ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) ) THEN ( CASE WHEN origin_amount = 0 THEN 'InvoiceStatus.none' WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN quantity = 0 and init_invoice_qty=0 THEN 'InvoiceStatus.none' WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END)
WHERE invoice_status_id IS NULL OR invoice_status_id != (CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' or ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) ) THEN ( CASE WHEN origin_amount = 0 THEN 'InvoiceStatus.none' WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN quantity = 0 and init_invoice_qty=0 THEN 'InvoiceStatus.none' WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END);


-- Trigger字段: openInvoiceStatusId (开票/开票申请执行状态)
-- 公式: CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) or coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' ) THEN ( CASE WHEN ABS ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ABS ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN ABS ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS(quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END
UPDATE revenue_confirmation_item
SET open_invoice_status_id = (CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) or coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' ) THEN ( CASE WHEN ABS ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ABS ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN ABS ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS(quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END)
WHERE open_invoice_status_id IS NULL OR open_invoice_status_id != (CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( CASE WHEN ( ( coalesce(product_standard_type_id, '') = 'ProductStandardType.quantity' and coalesce(adjust_object_type, '') != '' ) or coalesce(product_standard_type_id, '') != 'ProductStandardType.quantity' ) THEN ( CASE WHEN ABS ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ABS ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) >= ABS (origin_amount) THEN 'InvoiceStatus.all' WHEN ( ( origin_invoice_app_amount - origin_invoice_app_done_amount ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN origin_amount ELSE ( CASE WHEN ABS ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) >= ABS (origin_amount) THEN origin_amount ELSE ( origin_invoice_amount + origin_invoice_indir_declared_amount + origin_init_invoice_amount ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) ELSE ( CASE WHEN ABS ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS (quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) >= ABS (quantity) THEN 'InvoiceStatus.all' WHEN ( ( invoice_app_trans_qty - invoice_app_done_trans_qty ) +( CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN quantity ELSE ( CASE WHEN ABS( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) >= ABS(quantity) THEN quantity ELSE ( invoice_quantity + init_invoice_qty + invoice_indir_declared_trans_qty ) END ) END ) ) = 0 THEN 'InvoiceStatus.none' ELSE 'InvoiceStatus.part' END ) END ) END);


-- Trigger字段: originNotMakeInvoiceAmount (原币未开票金额)
-- 公式: origin_amount-(case when (invoice_item_id IS NOT NULL AND invoice_item_id !='')  then origin_amount else ( case when abs(origin_init_invoice_amount +origin_invoice_amount + origin_invoice_indir_declared_amount) > abs(origin_amount) then origin_amount else  (origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount) end )  end)
UPDATE revenue_confirmation_item
SET origin_not_make_invoice_amount = (origin_amount-(case when (invoice_item_id IS NOT NULL AND invoice_item_id !='')  then origin_amount else ( case when abs(origin_init_invoice_amount +origin_invoice_amount + origin_invoice_indir_declared_amount) > abs(origin_amount) then origin_amount else  (origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount) end )  end))
WHERE origin_not_make_invoice_amount IS NULL OR origin_not_make_invoice_amount != (origin_amount-(case when (invoice_item_id IS NOT NULL AND invoice_item_id !='')  then origin_amount else ( case when abs(origin_init_invoice_amount +origin_invoice_amount + origin_invoice_indir_declared_amount) > abs(origin_amount) then origin_amount else  (origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount) end )  end));


-- Trigger字段: originMakeInvoiceAmount (原币累计开票金额)
-- 公式: case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) > abs(origin_amount)  then origin_amount else ( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) end ) end
UPDATE revenue_confirmation_item
SET origin_make_invoice_amount = (case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) > abs(origin_amount)  then origin_amount else ( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) end ) end)
WHERE origin_make_invoice_amount IS NULL OR origin_make_invoice_amount != (case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) > abs(origin_amount)  then origin_amount else ( origin_init_invoice_amount + origin_invoice_amount + origin_invoice_indir_declared_amount ) end ) end);


-- Trigger字段: originOpenInvoiceAmount (原币可开票申请/开票金额)
-- 公式: origin_amount - ( origin_invoice_app_amount - origin_invoice_app_done_amount ) - ( case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) >= abs(origin_amount) then origin_amount else ( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) end ) end )
UPDATE revenue_confirmation_item
SET origin_open_invoice_amount = (origin_amount - ( origin_invoice_app_amount - origin_invoice_app_done_amount ) - ( case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) >= abs(origin_amount) then origin_amount else ( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) end ) end ))
WHERE origin_open_invoice_amount IS NULL OR origin_open_invoice_amount != (origin_amount - ( origin_invoice_app_amount - origin_invoice_app_done_amount ) - ( case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case when abs( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) >= abs(origin_amount) then origin_amount else ( origin_invoice_amount + origin_invoice_indir_declared_amount+origin_init_invoice_amount ) end ) end ));


-- ============================================
-- 对象: SalesContractItem
-- ============================================

-- 回写字段 (3个)


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originSalesInvoiceAmount
UPDATE sales_order_item
SET origin_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: SalesOrderItem.originSalesInvoiceAmount -> SalesContractItem.originSalesInvoiceAmount
UPDATE sales_contract_item
SET origin_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_sales_invoice_amount), 0)
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originRedSalesInvoiceAmount
UPDATE sales_order_item
SET origin_red_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: SalesOrderItem.originRedSalesInvoiceAmount -> SalesContractItem.originRedSalesInvoiceAmount
UPDATE sales_contract_item
SET origin_red_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_red_sales_invoice_amount), 0)
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originInvoiceAppliedAmount
UPDATE sales_order_item
SET origin_invoice_applied_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: SalesOrderItem.originInvoiceAppliedAmount -> SalesContractItem.originInvoiceAppliedAmount
UPDATE sales_contract_item
SET origin_invoice_applied_amount = (
    SELECT COALESCE(SUM(origin_invoice_applied_amount), 0)
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM sales_order_item
    WHERE sales_order_item.sales_contract_item_id = sales_contract_item.id
        AND sales_order_item.is_deleted = false
);


-- Trigger字段 (4个) - 使用UPDATE语句计算


-- Trigger字段: afterBillItemExecCount (后续单据明细执行情况)
-- 公式: (case when sales_order_trans_qty>0 or sales_order_amount>0 or issue_trans_qty>0 or issue_amount>0 or stock_out_trans_qty>0 or stock_out_amount >0  or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_req_amount >0  or pur_order_trans_qty>0 or pur_order_amount>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end)
UPDATE sales_contract_item
SET after_bill_item_exec_count = ((case when sales_order_trans_qty>0 or sales_order_amount>0 or issue_trans_qty>0 or issue_amount>0 or stock_out_trans_qty>0 or stock_out_amount >0  or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_req_amount >0  or pur_order_trans_qty>0 or pur_order_amount>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end))
WHERE after_bill_item_exec_count IS NULL OR after_bill_item_exec_count != ((case when sales_order_trans_qty>0 or sales_order_amount>0 or issue_trans_qty>0 or issue_amount>0 or stock_out_trans_qty>0 or stock_out_amount >0  or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_req_amount >0  or pur_order_trans_qty>0 or pur_order_amount>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end));


-- Trigger字段: originNetSalesInvoiceAmount (原币净开票金额)
-- 公式: origin_sales_invoice_amount - origin_red_sales_invoice_amount
UPDATE sales_contract_item
SET origin_net_sales_invoice_amount = (origin_sales_invoice_amount - origin_red_sales_invoice_amount)
WHERE origin_net_sales_invoice_amount IS NULL OR origin_net_sales_invoice_amount != (origin_sales_invoice_amount - origin_red_sales_invoice_amount);


-- Trigger字段: invoiceStatusId (开票状态)
-- 公式:  case decide_contract_status_path(contract_control_element_ids,product_standard_type_id,is_free_gift,trans_qty,origin_amount) when 'ContractStatusPath.twoStatus' then  two_status_id_or(decide_two_status_id(origin_sales_invoice_amount-origin_red_sales_invoice_amount,'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),decide_two_status_id_by_quantity(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),  sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB) when 'ContractStatusPath.ThreeStatusByAmount' then decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByQuantity' then decide_three_status_id_by_quantity ( coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByAmountAndQuantity' then three_status_id_or( decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),decide_three_status_id_by_quantity(coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_Qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),  '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB) else null end 
UPDATE sales_contract_item
SET invoice_status_id = ( case decide_contract_status_path(contract_control_element_ids,product_standard_type_id,is_free_gift,trans_qty,origin_amount) when 'ContractStatusPath.twoStatus' then  two_status_id_or(decide_two_status_id(origin_sales_invoice_amount-origin_red_sales_invoice_amount,'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),decide_two_status_id_by_quantity(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),  sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB) when 'ContractStatusPath.ThreeStatusByAmount' then decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByQuantity' then decide_three_status_id_by_quantity ( coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByAmountAndQuantity' then three_status_id_or( decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),decide_three_status_id_by_quantity(coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_Qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),  '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB) else null end )
WHERE invoice_status_id IS NULL OR invoice_status_id != ( case decide_contract_status_path(contract_control_element_ids,product_standard_type_id,is_free_gift,trans_qty,origin_amount) when 'ContractStatusPath.twoStatus' then  two_status_id_or(decide_two_status_id(origin_sales_invoice_amount-origin_red_sales_invoice_amount,'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),decide_two_status_id_by_quantity(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),  sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB),'["SalesInvoiceStatus.nothing","SalesInvoiceStatus.having"]'::JSONB) when 'ContractStatusPath.ThreeStatusByAmount' then decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByQuantity' then decide_three_status_id_by_quantity ( coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB)  when 'ContractStatusPath.ThreeStatusByAmountAndQuantity' then three_status_id_or( decide_three_status_id(origin_amount,origin_sales_invoice_amount-origin_red_sales_invoice_amount , '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),decide_three_status_id_by_quantity(coalesce(( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_contract_item.product_id=baseapp_product.ID),'SalesExecUnitType.transUnit'), trans_Qty, trans_aux_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, sales_invoice_trans_qty-red_sales_invoice_trans_qty, '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB),  '["SalesInvoiceStatus.none","SalesInvoiceStatus.partial","SalesInvoiceStatus.all"]'::JSONB) else null end );


-- Trigger字段: originUnSalesInvoiceAmount (原币可开票金额)
-- 公式: case when origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)<0 then 0 else origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount) end
UPDATE sales_contract_item
SET origin_un_sales_invoice_amount = (case when origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)<0 then 0 else origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount) end)
WHERE origin_un_sales_invoice_amount IS NULL OR origin_un_sales_invoice_amount != (case when origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)<0 then 0 else origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount) end);


-- ============================================
-- 对象: SalesIssueItem
-- ============================================

-- 回写字段 (1个)


-- ============================================
-- 对象: SalesOrder
-- ============================================

-- 回写字段 (3个)


-- 回写: InvoiceItem.originAmount -> SalesOrder.originInvoiceAmount
UPDATE sales_order
SET origin_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_id = sales_order.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_id = sales_order.id
        AND invoice_item.is_deleted = false
);


-- ============================================
-- 对象: SalesOrderItem
-- ============================================

-- 回写字段 (5个)


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originSalesInvoiceAmount
UPDATE sales_order_item
SET origin_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originRedSalesInvoiceAmount
UPDATE sales_order_item
SET origin_red_sales_invoice_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- 回写: InvoiceItem.originAmount -> SalesOrderItem.originInvoiceAppliedAmount
UPDATE sales_order_item
SET origin_invoice_applied_amount = (
    SELECT COALESCE(SUM(origin_amount), 0)
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
)
WHERE EXISTS (
    SELECT 1
    FROM invoice_item
    WHERE invoice_item.sales_order_item_id = sales_order_item.id
        AND invoice_item.is_deleted = false
);


-- Trigger字段 (6个) - 使用UPDATE语句计算


-- Trigger字段: originUnSalesInvoiceAmount (可开票金额)
-- 公式: case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) end 
UPDATE sales_order_item
SET origin_un_sales_invoice_amount = (case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) end )
WHERE origin_un_sales_invoice_amount IS NULL OR origin_un_sales_invoice_amount != (case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) end );


-- Trigger字段: originOpenInvoiceAmount (原币可开票申请或可开票金额)
-- 公式: case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) end
UPDATE sales_order_item
SET origin_open_invoice_amount = (case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) end)
WHERE origin_open_invoice_amount IS NULL OR origin_open_invoice_amount != (case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) < 0 then 0 else (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_invoice_applied_amount) - (origin_invoice_application_amount - origin_red_invoice_application_amount)) end);


-- Trigger字段: invoiceStatusId (开票状态)
-- 公式: CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE (SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 AND (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) AND trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN origin_amount<=(origin_sales_invoice_amount-origin_red_sales_invoice_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END
UPDATE sales_order_item
SET invoice_status_id = (CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE (SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 AND (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) AND trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN origin_amount<=(origin_sales_invoice_amount-origin_red_sales_invoice_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END)
WHERE invoice_status_id IS NULL OR invoice_status_id != (CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE (SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty)=0 AND (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN trans_qty<=(sales_invoice_trans_qty-red_sales_invoice_trans_qty) AND trans_aux_qty<=(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount)=0 THEN 'InvoiceStatus.none' WHEN origin_amount<=(origin_sales_invoice_amount-origin_red_sales_invoice_amount) THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END);


-- Trigger字段: openInvoiceStatusId (开票/开票申请执行状态)
-- 公式: CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE ( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)+(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 AND (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 AND (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)+(origin_invoice_application_amount-origin_red_invoice_application_amount)=0 THEN 'InvoiceStatus.none' WHEN (origin_amount-(origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)-(origin_invoice_application_amount-origin_red_invoice_application_amount))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END
UPDATE sales_order_item
SET open_invoice_status_id = (CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE ( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)+(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 AND (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 AND (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)+(origin_invoice_application_amount-origin_red_invoice_application_amount)=0 THEN 'InvoiceStatus.none' WHEN (origin_amount-(origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)-(origin_invoice_application_amount-origin_red_invoice_application_amount))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END)
WHERE open_invoice_status_id IS NULL OR open_invoice_status_id != (CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE ( SELECT sales_exec_unit_type_id FROM baseapp_product WHERE sales_sales_order_item.product_id=baseapp_product.ID) WHEN 'SalesExecUnitType.transUnit' THEN CASE WHEN (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END WHEN 'SalesExecUnitType.transAuxUnit' THEN CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END ELSE CASE WHEN (sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)+(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty)=0 AND (sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)+(invoice_application_trans_qty-red_invoice_application_trans_qty)=0 THEN 'InvoiceStatus.none' WHEN (trans_qty-(sales_invoice_trans_qty-red_sales_invoice_trans_qty-invoice_applied_trans_qty)-(invoice_application_trans_qty-red_invoice_application_trans_qty))<=0 AND (trans_aux_qty-(sales_invoice_trans_aux_qty-red_sales_invoice_trans_aux_qty-invoice_applied_trans_aux_qty)-(invoice_application_trans_aux_qty-red_invoice_application_trans_aux_qty))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END ELSE CASE WHEN (origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)+(origin_invoice_application_amount-origin_red_invoice_application_amount)=0 THEN 'InvoiceStatus.none' WHEN (origin_amount-(origin_sales_invoice_amount-origin_red_sales_invoice_amount-origin_invoice_applied_amount)-(origin_invoice_application_amount-origin_red_invoice_application_amount))<=0 THEN 'InvoiceStatus.all' ELSE 'InvoiceStatus.part' END END);


-- Trigger字段: afterBillItemExecCount (后续单据明细执行情况)
-- 公式: (case when issue_trans_qty>0 or stock_out_trans_qty>0 or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_order_trans_qty>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end)
UPDATE sales_order_item
SET after_bill_item_exec_count = ((case when issue_trans_qty>0 or stock_out_trans_qty>0 or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_order_trans_qty>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end))
WHERE after_bill_item_exec_count IS NULL OR after_bill_item_exec_count != ((case when issue_trans_qty>0 or stock_out_trans_qty>0 or revenue_confirm_trans_qty>0 or invoice_application_trans_qty>0 or sales_invoice_trans_qty>0 or sales_receipt_amount>0 or pur_req_trans_qty>0 or pur_order_trans_qty>0 or origin_revenue_confirm_amount>0 or origin_invoice_application_amount>0 or origin_sales_invoice_amount>0 or origin_sales_receipt_amount>0 then 1 else 0 end));


-- Trigger字段: originNetSalesInvoiceAmount (净开票金额)
-- 公式: origin_sales_invoice_amount - origin_red_sales_invoice_amount
UPDATE sales_order_item
SET origin_net_sales_invoice_amount = (origin_sales_invoice_amount - origin_red_sales_invoice_amount)
WHERE origin_net_sales_invoice_amount IS NULL OR origin_net_sales_invoice_amount != (origin_sales_invoice_amount - origin_red_sales_invoice_amount);
