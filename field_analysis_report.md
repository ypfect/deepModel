# Excel字段解析报告

## 概述

- **总对象数**: 13
- **解析时间**: 自动生成


## 字段分类统计

| 对象 | 回写字段 | Trigger字段 | Expression字段 | Virtual字段 | 普通字段 |
|------|---------|------------|--------------|------------|---------|
| ArContract | 5 | 0 | 0 | 0 | 0 |
| ArContractSubjectMatterItem | 9 | 11 | 0 | 1 | 0 |
| InstallmentRcPlanItem | 1 | 0 | 0 | 0 | 0 |
| Invoice | 13 | 2 | 0 | 0 | 0 |
| InvoiceApplication | 4 | 0 | 0 | 0 | 0 |
| InvoiceApplicationItem | 1 | 3 | 0 | 0 | 0 |
| InvoiceItem | 0 | 11 | 0 | 1 | 1 |
| RevenueConfirmation | 8 | 0 | 0 | 0 | 0 |
| RevenueConfirmationItem | 5 | 7 | 0 | 0 | 0 |
| SalesContractItem | 3 | 4 | 0 | 0 | 0 |
| SalesIssueItem | 1 | 0 | 0 | 0 | 0 |
| SalesOrder | 3 | 0 | 0 | 0 | 0 |
| SalesOrderItem | 5 | 6 | 0 | 1 | 0 |

## 详细字段列表


### ArContract


#### 回写字段 (5个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originExecuteInvoiceAmount | 非期初执行开票原币金额 |  |
| invoiceStatusId | 开票状态 |  |
| originOpenInvoiceAmount | 可开票原币金额 |  |
| originAppInvoiceAmount | 开票申请已开票原币金额 |  |
| originInAcAmount | 发票立账发票原币金额 |  |

### ArContractSubjectMatterItem


#### 回写字段 (9个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originExecuteInvoiceAmountDir | 非期初执行开票原币金额（直接执行） | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originExecuteInvo... |
| originExecuteAppInvoiceAmountDir | 非期初开票申请已开票原币金额（直接执行） | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originExecuteAppI... |
| originExecuteInvoiceAmountFrame | 非期初执行开票原币金额（合同执行） | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originExecuteInvo... |
| originInAcAmountFrame | 发票立账发票原币金额(合同执行) | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originInAcAmountF... |
| originInAcAmountDir | 发票立账发票原币金额(直接执行) | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originInAcAmountD... |
| originExecuteAppInvoiceAmountFrame | 非期初开票申请已开票原币金额（合同执行） | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originExecuteAppI... |
| originExecuteAppInvoiceAmountDirForValid | 非期初开票申请已开票原币金额（直接执行）为了校验 | InvoiceItem.originAmount --[回写]--> ArContractSubjectMatterItem.originExecuteAppI... |
| originInvoiceMakeAppAmountFrame | 原币开票申请金额(已关闭，合同执行) | InvoiceItem.originAmount --[回写]--> InvoiceApplicationItem.originMakeInvoiceAmoun... |
| originInvoiceMakeAppAmountDir | 原币开票申请金额(已关闭，直接执行) | InvoiceItem.originAmount --[回写]--> InvoiceApplicationItem.originMakeInvoiceAmoun... |

#### Trigger字段 (11个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originExecuteInvoiceAmount | 非期初执行开票原币金额 | `origin_execute_invoice_amount_dir + origin_execute_invoice_amount_frame` |
| invoiceStatusId | 开票状态 | `CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_...` |
| originInvoiceAmount | 开票原币金额 | `origin_init_invoice_amount_dir + origin_init_invoice_amount_frame +origin_execute_invoice_amount_dir...` |
| originInvoiceEffAmount | 有效开票原币金额 | `(origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make...` |
| originOpenInvoiceAmount | 可开票原币金额 | `case when origin_amount=0 then 0 else (origin_amount - ((origin_init_invoice_app_amount_dir + origin...` |
| originInvoiceEffAmountForValid | 有效开票原币金额为了校验 | `(origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make...` |
| originAppInvoiceAmount | 开票申请已开票原币金额 | `origin_init_app_invoice_amount_dir+origin_init_app_invoice_amount_frame +origin_execute_app_invoice_...` |
| originInAcAmount | 发票立账发票原币金额 | `origin_in_ac_amount_dir + origin_in_ac_amount_frame` |
| originInvoiceMakeAppAmount | 原币开票申请金额(已关闭) | `origin_invoice_make_app_amount_dir + origin_invoice_make_app_amount_frame` |
| originInvoiceAppAmount | 开票申请原币金额 | `origin_init_invoice_app_amount_dir + origin_init_invoice_app_amount_frame + origin_invoice_not_make_...` |
| invoiceAppStatusId | 开票申请状态 | `CASE WHEN 'GW8GVT50KC00001' = ( SELECT business_type_id FROM contract_ar_contract WHERE contract_ar_...` |

### InstallmentRcPlanItem


#### 回写字段 (1个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| invoiceAmount | 开票金额 |  |

### Invoice


#### 回写字段 (13个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originAmount | 原币金额合计 | InvoiceItem.originAmount --[回写]--> Invoice.originAmount |
| originRedAmount | 原币累计红字发票金额合计 | InvoiceItem.originAmount --[回写]--> Invoice.originRedAmount |
| originRedEffAmount | 原币生效结案态红字发票金额 | InvoiceItem.originAmount --[回写]--> Invoice.originRedEffAmount |
| receiptStatusId | 收款状态 | InvoiceItem.originAmount --[触发]--> InvoiceItem.receiptStatusId → InvoiceItem.rec... |
| invoiceReceiptStatusId | 发票收入确认状态 | InvoiceItem.originAmount --[触发]--> InvoiceItem.invoiceReceiptStatusId → InvoiceI... |
| originNoRequiredReceiptAmount | 原币无需收款金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originNoRequiredReceiptAmount → I... |
| originRealDoDeclaredAmount | 原币实际未核销金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originRealDoDeclaredAmount → Invo... |
| originNotReceiptAmount | 原币未收款金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originNotReceiptAmount → InvoiceI... |
| originNotRcfAmount | 原币未收入确认金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originNotRcfAmount → InvoiceItem.... |
| originInvoiceDoDeclaredAmount | 原币发票未核销金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originInvoiceDoDeclaredAmount → I... |
| originRcfAmount | 原币累计收入确认金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originRcfAmount → InvoiceItem.ori... |
| originDoDeclaredAmount | 原币未核销金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originDoDeclaredAmount → InvoiceI... |
| originReceiptAmount | 原币累计收款金额 | InvoiceItem.originAmount --[触发]--> InvoiceItem.originReceiptAmount → InvoiceItem... |

#### Trigger字段 (2个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originNotRedAmount | 原币未红冲金额 | `origin_amount-origin_red_amount` |
| issuedInvoiceStatusId | 开税票状态 | `case when bill_status in ('BillStatus.effective','BillStatus.submitted', 'BillStatus.approving') the...` |

### InvoiceApplication


#### 回写字段 (4个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originMakeInvoiceAmount | 累计原币开票金额合计 | InvoiceItem.originAmount --[回写]--> InvoiceApplication.originMakeInvoiceAmount |
| redRcfAmount | 累计原币红字开票申请金额合计 | InvoiceItem.originAmount --[回写]--> InvoiceApplicationItem.originMakeInvoiceAmoun... |
| originEffectiveAppAmount | 原币有效开票申请金额合计 |  |
| invoiceStatusId | 开票状态 |  |

### InvoiceApplicationItem


#### 回写字段 (1个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originMakeInvoiceAmount | 累计原币开票金额 | InvoiceItem.originAmount --[回写]--> InvoiceApplicationItem.originMakeInvoiceAmoun... |

#### Trigger字段 (3个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originEffectiveAppAmount | 原币有效开票申请金额 | `case when (closed_status = 'ClosedStatus.closed' or closed_status = 'ClosedStatus.opening') then coa...` |
| originOpenInvoiceAmount | 原币未开票金额 | `origin_amount - origin_make_invoice_amount` |
| invoiceStatusId | 开票状态 | `CASE WHEN (product_standard_type_id IS NOT NULL AND product_standard_type_id='ProductStandardType.qu...` |

### InvoiceItem


#### Trigger字段 (11个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originNotRedAmount | 原币未红冲金额 | `origin_amount-origin_red_amount` |
| originRealInvoiceDoDeclaredAmount | 原币发票实际未核销金额 | `origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount ` |
| receiptStatusId | 收款状态 | `CASE WHEN is_free_gift THEN 'ReceiptStatus.all' WHEN ABS (CASE WHEN ( SELECT is_accounting_bill FROM...` |
| originNoRequiredReceiptAmount | 原币无需收款金额 | `CASE WHEN ABS (origin_inv_verify_no_required_receipt_amount)> ABS ((origin_amount-(CASE WHEN ( SELEC...` |
| originRealDoDeclaredAmount | 原币实际未核销金额 | `origin_amount - origin_done_declared_amount - origin_invoice_receipt_amount - ( origin_dir_receipt_a...` |
| originNotReceiptAmount | 原币未收款金额 | `origin_amount-(CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoi...` |
| originNotRcfAmount | 原币未收入确认金额 | `origin_amount-(case when (abs(origin_init_rc_amount)+abs(origin_revenue_amount)+abs(origin_red_eff_a...` |
| originInvoiceDoDeclaredAmount | 原币发票未核销金额 | `origin_amount - origin_invoice_done_declared_amount -origin_init_rc_amount` |
| originRcfAmount | 原币累计收入确认金额 | `case when ( abs(origin_revenue_amount)+ abs(origin_init_rc_amount) + abs(origin_red_eff_amount) ) > ...` |
| originDoDeclaredAmount | 原币未核销金额 | `origin_amount - origin_done_declared_amount-origin_init_receipt_amount -origin_invoice_receipt_amoun...` |
| originReceiptAmount | 原币累计收款金额 | `CASE WHEN ( SELECT is_accounting_bill FROM arap_invoice WHERE arap_invoice_item.invoice_id=arap_invo...` |

### RevenueConfirmation


#### 回写字段 (8个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originInvoiceAmount | 原币已开票金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmation.originInvoiceAmount |
| originInvoiceAppDoneAmount | 原币累计开票申请已开票金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmation.originInvoiceAppDoneAmoun... |
| originInvoiceReserveAmount | 原币发票预占金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmation.originInvoiceReserveAmoun... |
| originInvoiceAppAmount | 原币累计开票申请金额 |  |
| invoiceStatusId | 开票状态 |  |
| originNotMakeInvoiceAmount | 原币未开票金额 |  |
| originMakeInvoiceAmount | 原币累计开票金额 |  |
| originCanInvoiceAmount | 原币未开票申请/开票金额 |  |

### RevenueConfirmationItem


#### 回写字段 (5个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originEffInvoiceAmount | 原币已生效开票金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmationItem.originEffInvoiceAmoun... |
| originInvoiceAmount | 原币已开票金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmationItem.originInvoiceAmount |
| originInvoiceAppDoneAmount | 原币累计开票申请已开票金额 | InvoiceItem.originAmount --[回写]--> RevenueConfirmationItem.originInvoiceAppDoneA... |
| originInvoiceAppReserveAmount | 原币开票申请预占金额 | InvoiceItem.originAmount --[回写]--> InvoiceApplicationItem.originMakeInvoiceAmoun... |
| originInvoiceAppAmount | 原币累计开票申请金额 |  |

#### Trigger字段 (7个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originInvoiceReserveAmount | 原币发票预占金额 | `(origin_invoice_amount - origin_eff_invoice_amount) + origin_invoice_app_reserve_amount ` |
| originRealInvoiceDoDeclaredAmount | 原币发票实际未核销金额 | `origin_amount - origin_invoice_done_declared_amount - (origin_invoice_amount - origin_eff_invoice_am...` |
| invoiceStatusId | 开票状态 | `CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( ...` |
| openInvoiceStatusId | 开票/开票申请执行状态 | `CASE WHEN ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) THEN 'InvoiceStatus.all' ELSE ( ...` |
| originNotMakeInvoiceAmount | 原币未开票金额 | `origin_amount-(case when (invoice_item_id IS NOT NULL AND invoice_item_id !='')  then origin_amount ...` |
| originMakeInvoiceAmount | 原币累计开票金额 | `case when ( invoice_item_id IS NOT NULL AND invoice_item_id != '' ) then origin_amount else ( case w...` |
| originOpenInvoiceAmount | 原币可开票申请/开票金额 | `origin_amount - ( origin_invoice_app_amount - origin_invoice_app_done_amount ) - ( case when ( invoi...` |

### SalesContractItem


#### 回写字段 (3个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originSalesInvoiceAmount | 原币开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originSalesInvoiceAmount → Sal... |
| originRedSalesInvoiceAmount | 原币红字开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originRedSalesInvoiceAmount → ... |
| originInvoiceAppliedAmount | 原币开票申请已开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originInvoiceAppliedAmount → S... |

#### Trigger字段 (4个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| afterBillItemExecCount | 后续单据明细执行情况 | `(case when sales_order_trans_qty>0 or sales_order_amount>0 or issue_trans_qty>0 or issue_amount>0 or...` |
| originNetSalesInvoiceAmount | 原币净开票金额 | `origin_sales_invoice_amount - origin_red_sales_invoice_amount` |
| invoiceStatusId | 开票状态 | ` case decide_contract_status_path(contract_control_element_ids,product_standard_type_id,is_free_gift...` |
| originUnSalesInvoiceAmount | 原币可开票金额 | `case when origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)<0 then 0 e...` |

### SalesIssueItem


#### 回写字段 (1个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originInvoiceAppAmount | 原币开票申请金额 |  |

### SalesOrder


#### 回写字段 (3个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originInvoiceAmount | 原币已开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrder.originInvoiceAmount |
| invoiceStatusId | 开票状态 |  |
| invoiceWithoutClosedStatusId | 开票状态-剔除关闭行 |  |

### SalesOrderItem


#### 回写字段 (5个)

| 字段名 | 标题 | 影响路径 |
|--------|------|----------|
| originSalesInvoiceAmount | 开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originSalesInvoiceAmount |
| originRedSalesInvoiceAmount | 红字开票金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originRedSalesInvoiceAmount |
| originInvoiceAppliedAmount | 已开票申请金额 | InvoiceItem.originAmount --[回写]--> SalesOrderItem.originInvoiceAppliedAmount |
| originInvoiceApplicationAmount | 开票申请金额 |  |
| originRedInvoiceApplicationAmount | 红字开票申请金额 |  |

#### Trigger字段 (6个)

| 字段名 | 标题 | 触发公式 |
|--------|------|----------|
| originUnSalesInvoiceAmount | 可开票金额 | `case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount)) < 0 then...` |
| originOpenInvoiceAmount | 原币可开票申请或可开票金额 | `case when (origin_amount - (origin_sales_invoice_amount - origin_red_sales_invoice_amount - origin_i...` |
| invoiceStatusId | 开票状态 | `CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE (SELECT sales_exec_unit_...` |
| openInvoiceStatusId | 开票/开票申请执行状态 | `CASE WHEN product_standard_type_id='ProductStandardType.quantity' THEN CASE ( SELECT sales_exec_unit...` |
| afterBillItemExecCount | 后续单据明细执行情况 | `(case when issue_trans_qty>0 or stock_out_trans_qty>0 or revenue_confirm_trans_qty>0 or invoice_appl...` |
| originNetSalesInvoiceAmount | 净开票金额 | `origin_sales_invoice_amount - origin_red_sales_invoice_amount` |