/*
 * Copyright (c) 2005-2010 Grameen Foundation USA
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */

package org.mifos.ui.core.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.mifos.application.admin.servicefacade.AdminServiceFacade;
import org.mifos.dto.domain.PrdOfferingDto;
import org.mifos.dto.domain.ProductTypeDto;
import org.mifos.dto.screen.ProductMixPreviewDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/defineProductMix")
@SessionAttributes("formBean")
public class DefineProductMixController {

    private static final String REDIRECT_TO_ADMIN_SCREEN = "redirect:/AdminAction.do?method=load";
    private static final String CANCEL_PARAM = "CANCEL";

    @Autowired
    private AdminServiceFacade adminServiceFacade;

    protected DefineProductMixController() {
        // default contructor for spring autowiring
    }

    public DefineProductMixController(AdminServiceFacade adminServiceFacade) {
        this.adminServiceFacade = adminServiceFacade;
    }

    @RequestMapping(method = RequestMethod.GET)
    @ModelAttribute("formBean")
    public ProductMixFormBean showPopulatedForm() {

        ProductMixFormBean defineFormBean = new ProductMixFormBean();
        populateProductTypeOptions(defineFormBean);

        return defineFormBean;
    }

    private void populateProductTypeOptions(ProductMixFormBean formBean) {
        Map<String, String> productTypeOptions = new LinkedHashMap<String, String>();

        List<ProductTypeDto> productTypes = this.adminServiceFacade.retrieveProductTypesApplicableToProductMix();
        for (ProductTypeDto productTypeDto : productTypes) {
            productTypeOptions.put(productTypeDto.getProductTypeId().toString(), productTypeDto.getProductTypeKey());
        }

        formBean.setProductTypeOptions(productTypeOptions);
    }

    @RequestMapping(method = RequestMethod.POST)
    public ModelAndView processFormSubmit(@RequestParam(value = CANCEL_PARAM, required = false) String cancel,
                                    @ModelAttribute("formBean") ProductMixFormBean formBean,
                                    BindingResult result,
                                    SessionStatus status) {

        ModelAndView mav = new ModelAndView(REDIRECT_TO_ADMIN_SCREEN);

        if (StringUtils.isNotBlank(cancel)) {
            status.setComplete();
        } else if (result.hasErrors()) {
            mav = new ModelAndView("defineProductMix");
        } else {
            mav = new ModelAndView("defineProductMix");

            if (StringUtils.isBlank(formBean.getProductTypeId())) {
                ProductMixFormBean defineFormBean = new ProductMixFormBean();
                populateProductTypeOptions(defineFormBean);
            } else if (StringUtils.isNotBlank(formBean.getProductTypeId()) && StringUtils.isBlank(formBean.getProductId())) {
                populateProductNameOptions(formBean);
            } else if (StringUtils.isNotBlank(formBean.getProductTypeId()) &&
                    StringUtils.isNotBlank(formBean.getProductId()) && (formBean.getAllowed() == null && formBean.getNotAllowed() == null)) {
                populateAllowedNotAllowedOptions(formBean);

            } else {
                String selectedProductTypeNameKey = formBean.getProductTypeOptions().get(formBean.getProductTypeId());
                String selectedProductName = formBean.getProductNameOptions().get(formBean.getProductId());

                List<String> allowedProductMix = findMatchingProducts(formBean, formBean.getAllowed());
                List<String> notAllowedProductMix = findMatchingProducts(formBean, formBean.getNotAllowed());

                ProductMixPreviewDto preview = new ProductMixPreviewDto();
                preview.setProductTypeNameKey(selectedProductTypeNameKey);
                preview.setProductName(selectedProductName);
                preview.setAllowedProductMix(allowedProductMix);
                preview.setNotAllowedProductMix(notAllowedProductMix);

                mav = new ModelAndView("previewProductMix");
                mav.addObject("ref", preview);
                mav.addObject("formView", "defineProductMix");
            }

        }

        return mav;
    }

    private void populateProductNameOptions(ProductMixFormBean formBean) {
        Map<String, String> productNameOptions = new LinkedHashMap<String, String>();

        List<PrdOfferingDto> applicableProducts = this.adminServiceFacade.retrieveLoanProductsNotMixed();
        for (PrdOfferingDto product : applicableProducts) {
            productNameOptions.put(product.getPrdOfferingId().toString(), product.getPrdOfferingName());
        }

        formBean.setProductNameOptions(productNameOptions);
    }

    private void populateAllowedNotAllowedOptions(ProductMixFormBean formBean) {
        Map<String, String> allowedProductOptions = new LinkedHashMap<String, String>();
        Map<String, String> notAllowedProductOptions = new LinkedHashMap<String, String>();

        List<PrdOfferingDto> allowedProductsInMix = this.adminServiceFacade.retrieveAllowedProductsForMix(Integer.parseInt(formBean.getProductTypeId()), Integer.parseInt(formBean.getProductId()));
        for (PrdOfferingDto allowedProduct : allowedProductsInMix) {
            allowedProductOptions.put(allowedProduct.getPrdOfferingId().toString(), allowedProduct.getPrdOfferingName());
        }

        List<PrdOfferingDto> notAllowedProductsInMix = this.adminServiceFacade.retrieveNotAllowedProductsForMix(Integer.parseInt(formBean.getProductTypeId()), Integer.parseInt(formBean.getProductId()));
        for (PrdOfferingDto notAllowedProduct : notAllowedProductsInMix) {
            notAllowedProductOptions.put(notAllowedProduct.getPrdOfferingId().toString(), notAllowedProduct.getPrdOfferingName());
        }

        formBean.setAllowedProductOptions(allowedProductOptions);
        formBean.setNotAllowedProductOptions(notAllowedProductOptions);
    }

    private List<String> findMatchingProducts(ProductMixFormBean formBean, String[] productsArray) {
        List<String> allowedProductNames = new ArrayList<String>();

        if (productsArray != null) {
            for (String productKey : productsArray) {
                if (formBean.getAllowedProductOptions().containsKey(productKey)) {
                    allowedProductNames.add(formBean.getAllowedProductOptions().get(productKey));
                } else if (formBean.getNotAllowedProductOptions().containsKey(productKey)) {
                    allowedProductNames.add(formBean.getNotAllowedProductOptions().get(productKey));
                }
            }
        }

        return allowedProductNames;
    }
}