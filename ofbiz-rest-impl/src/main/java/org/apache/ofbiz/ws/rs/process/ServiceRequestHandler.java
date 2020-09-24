/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.ws.rs.process;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericEntityNotFoundException;
import org.apache.ofbiz.entity.GenericNoSuchEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ModelParam;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.ServiceValidationException;
import org.apache.ofbiz.ws.rs.core.ResponseStatus;
import org.apache.ofbiz.ws.rs.response.Error;
import org.apache.ofbiz.ws.rs.util.RestApiUtil;
import org.codehaus.groovy.runtime.InvokerInvocationException;

public final class ServiceRequestHandler extends RestRequestHandler {

    private static final String MODULE = ServiceRequestHandler.class.getName();
    private static final String DEFAULT_MSG_UI_LABEL_RESOURCE = "ApiUiLabels";
    private String service;

    public ServiceRequestHandler(String service) {
        this.service = service;
    }

    /**
     * @param data
     * @return
     */
    @Override
    protected Response execute(ContainerRequestContext data, Map<String, Object> arguments) {
        LocalDispatcher dispatcher = (LocalDispatcher) getServletContext().getAttribute("dispatcher");
        Map<String, Object> serviceContext = null;
        try {
            serviceContext = dispatcher.getDispatchContext().makeValidContext(service, ModelService.IN_PARAM, arguments);
        } catch (GenericServiceException e) {
            Debug.logError(e, MODULE);
        }
        ModelService svc = getModelService(dispatcher.getDispatchContext());
        GenericValue userLogin = (GenericValue) getHttpRequest().getAttribute("userLogin");
        serviceContext.put("userLogin", userLogin);
        Map<String, Object> result = null;
        try {
            result = dispatcher.runSync(service, serviceContext);
        } catch (GenericServiceException e) {
            return handleException(e);
        }
        Map<String, Object> responseData = new LinkedHashMap<>();
        if (ServiceUtil.isSuccess(result)) {
            Set<String> outParams = svc.getOutParamNames();
            for (String outParamName : outParams) {
                ModelParam outParam = svc.getParam(outParamName);
                if (!outParam.isInternal()) {
                    Object value = result.get(outParamName);
                    if (UtilValidate.isNotEmpty(value)) {
                        responseData.put(outParamName, value);
                    }
                }
            }
            return RestApiUtil.success((String) result.get(ModelService.SUCCESS_MESSAGE), responseData);
        } else {
            return errorFromServiceResult(service, result);
        }
    }

    private ModelService getModelService(DispatchContext dispatchContext) {
        ModelService svc = null;
        try {
            svc = dispatchContext.getModelService(service);
        } catch (GenericServiceException gse) {
            throw new NotFoundException(gse.getMessage());
        }
        return svc;
    }

    private Response handleException(GenericServiceException gse) {
        Response.ResponseBuilder builder = null;
        Throwable actualCause = gse.getCause();
        if (actualCause == null) {
            actualCause = gse;
        } else if (actualCause instanceof InvokerInvocationException) {
            actualCause = actualCause.getCause();
        }

        if (actualCause instanceof ServiceValidationException) {
            ServiceValidationException validationException = (ServiceValidationException) actualCause;
            Error error = new Error().type(actualCause.getClass().getSimpleName()).code(Response.Status.BAD_REQUEST.getStatusCode())
                    .description(Response.Status.BAD_REQUEST.getReasonPhrase())
                    .message(getErrorMessage(service, "GenericServiceValidationErrorMessage", getHttpRequest().getLocale()))
                    .errorDesc((validationException.getMessage())).additionalErrors(validationException.getMessageList());
            builder = Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(error);
        } else if (actualCause instanceof GenericNoSuchEntityException
                || actualCause instanceof GenericEntityNotFoundException) {
            Error error = new Error().type(actualCause.getClass().getSimpleName()).code(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                    .description(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
                    .message(getErrorMessage(service, "NoSuchEntityDefaultMessage", getHttpRequest().getLocale()))
                    .errorDesc(ExceptionUtils.getRootCauseMessage(gse));
            builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(error);
        } else if (actualCause instanceof GenericEntityException) {
            Error error = new Error().type(actualCause.getClass().getSimpleName()).code(ResponseStatus.Custom.UNPROCESSABLE_ENTITY.getStatusCode())
                    .description(ResponseStatus.Custom.UNPROCESSABLE_ENTITY.getReasonPhrase())
                    .message(getErrorMessage(service, "GenericServiceExecutionGenericEntityOperationErrorMessage", getHttpRequest().getLocale()))
                    .errorDesc(ExceptionUtils.getRootCauseMessage(gse));
            builder = Response.status(ResponseStatus.Custom.UNPROCESSABLE_ENTITY).type(MediaType.APPLICATION_JSON)
                    .entity(error);
        } else {
            Error error = new Error().type(actualCause.getClass().getSimpleName()).code(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
                    .description(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
                    .message(getErrorMessage(service, "GenericServiceExecutionGenericExceptionErrorMessage", getHttpRequest().getLocale()))
                    .errorDesc(ExceptionUtils.getRootCauseMessage(gse));
            builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(error);
        }
        return builder.build();
    }

    private String getErrorMessage(String serviceName, String errorKey, Locale locale) {
        String error = UtilProperties.getMessage(DEFAULT_MSG_UI_LABEL_RESOURCE, errorKey, locale);
        error = error.replace("${service}", serviceName);
        return error;
    }

    @SuppressWarnings("unchecked")
    private Response errorFromServiceResult(String service, Map<String, Object> result) {
        String errorMessage = null;
        List<String> additionalErrorMessages = new LinkedList<>();
        if (!UtilValidate.isEmpty(result.get(ModelService.ERROR_MESSAGE))) {
            errorMessage = result.get(ModelService.ERROR_MESSAGE).toString();
        }
        if (!UtilValidate.isEmpty(result.get(ModelService.ERROR_MESSAGE_LIST))) {
            List<String> errorMessageList = (List<String>) result.get(ModelService.ERROR_MESSAGE_LIST);
            if (UtilValidate.isEmpty(errorMessage)) {
                errorMessage = errorMessageList.get(0);
                errorMessageList.remove(0);
            }
            for (int i = 0; i < errorMessageList.size(); i++) {
                additionalErrorMessages.add(errorMessageList.get(i));
            }
        }
        Error error = new Error().type("ServiceError").code(ResponseStatus.Custom.UNPROCESSABLE_ENTITY.getStatusCode())
                .description(ResponseStatus.Custom.UNPROCESSABLE_ENTITY.getReasonPhrase())
                .message(getErrorMessage(service, "GenericServiceErrorMessage", getHttpRequest().getLocale()))
                .errorDesc(errorMessage);
        return Response.status(ResponseStatus.Custom.UNPROCESSABLE_ENTITY).type(MediaType.APPLICATION_JSON).entity(error).build();
    }
}
