package org.apache.ofbiz.graphql.product.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.condition.EntityFunction;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityFindOptions;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.graphql.schema.PaginationInputType;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;

/**
 *
 * @author grv
 *
 */
public class GQLProductServices {

    public static final String MODULE = GQLProductServices.class.getName();

    /**
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> searchProductsByGoodIdentificationValue(DispatchContext dctx,
            Map<String, Object> context) {

        Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        String idFragment = (String) context.get("idFragment");
        List<EntityExpr> exprs = UtilMisc.toList(EntityCondition.makeCondition(
                    EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.IN, UtilMisc.toList("SKU", "UPC", "ISBN")),EntityOperator.AND,
                        EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("idValue"),
                                EntityOperator.LIKE, EntityFunction.UPPER(((String) "%" + idFragment + "%").toUpperCase()))));

        EntityFindOptions options = new EntityFindOptions();
        String orderBy = null;
        if(context.containsKey("pagination")) {
            PaginationInputType pagination = (PaginationInputType) context.get("pagination");
            options.setLimit(pagination.pageSize);
            options.setMaxRows(pagination.pageSize);
            options.setOffset(pagination.pageIndex);
            orderBy = pagination.orderByField;
        }

        int totalCount = 0;
        try {
            totalCount = (int)delegator.findCountByCondition("ProductAndGoodIdentification", EntityCondition.makeCondition(exprs), null, options);
            System.out.println("That's count: "+totalCount);
            List<GenericValue> productGIViewList = delegator.findList("ProductAndGoodIdentification", EntityCondition.makeCondition(exprs), null, UtilValidate.isNotEmpty(orderBy) ? Arrays.asList(orderBy.split(",")) : null, options, false);
            System.out.println("productGIViewList: "+productGIViewList);
            Map<String, Object> buildConnectionCtx = null;
            buildConnectionCtx = dctx.makeValidContext("buildConnection", ModelService.IN_PARAM, context);
            buildConnectionCtx.put("el", productGIViewList);
            buildConnectionCtx.put("totalCount", totalCount);
            if(context.containsKey("pagination")) {
                PaginationInputType pagination = (PaginationInputType) context.get("pagination");
                buildConnectionCtx.put("pageIndex", pagination.pageIndex);
                buildConnectionCtx.put("pageSize", pagination.pageSize);
            }
            serviceResult = dispatcher.runSync("buildConnection", buildConnectionCtx);
        } catch (GenericEntityException | GenericServiceException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return serviceResult;
    }

    /**
     *
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> searchProductsByName(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
        Delegator delegator = dctx.getDelegator();
        String nameFragment = (String) context.get("nameFragment");
        List<EntityExpr> exprs = UtilMisc.toList(EntityCondition.makeCondition(
                EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.IN,
                        UtilMisc.toList("SKU", "UPC", "ISBN")),
                EntityOperator.AND, EntityCondition.makeCondition(EntityFunction.UPPER_FIELD("productName"),
                        EntityOperator.LIKE, EntityFunction.UPPER(((String) "%" + nameFragment + "%").toUpperCase()))));
        try {
            List<GenericValue> productGIViewList = EntityQuery.use(delegator).from("ProductAndGoodIdentification")
                    .where(exprs).queryList();
            serviceResult.put("products", productGIViewList);
        } catch (GenericEntityException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return ServiceUtil.returnError(e.getMessage());
        }
        return serviceResult;
    }

    public static Map<String, Object> createProduct(DispatchContext dctx, Map<String, Object> context){
        Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispather = dctx.getDispatcher();
        try {
            Map<String, Object> newContext = dctx.makeValidContext("createProduct", ModelService.IN_PARAM, context);
            serviceResult = dispather.runSync("createProduct", newContext);
        } catch (GenericServiceException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if(ServiceUtil.isSuccess(serviceResult)) {
             GenericValue product = null;
            try {
                product = EntityQuery.use(delegator).from("Product").where("productId", serviceResult.get("productId")).cache().queryOne();
                serviceResult.put("_graphql_result_", product);
            } catch (GenericEntityException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return serviceResult;
    }

    public static Map<String, Object> createOrder(DispatchContext dctx, Map<String, Object> context){
        System.out.println("context for createOrder"+context);
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> getProductDetail(DispatchContext dctx, Map<String, Object> context){
        System.out.println("context for getProductDetail"+context);
        Map<String, Object> serviceResult = ServiceUtil.returnSuccess();
        String productId = (String)context.get("id");
        Delegator delegator = dctx.getDelegator();
        GenericValue product = null;
        try {
            product = EntityQuery.use(delegator).from("Product").where("productId", productId).cache().queryOne();
        }catch(GenericEntityException e) {
            e.printStackTrace();
        }

        Map<String, Object> productDetail = new HashMap<String, Object>(product);
        productDetail.put("availablePublicationCount", 3);
        productDetail.putAll(product);
        serviceResult.put("_graphql_result_", productDetail);
        return serviceResult;
    }
}
