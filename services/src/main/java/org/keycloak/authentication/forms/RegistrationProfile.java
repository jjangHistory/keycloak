/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authentication.forms;

import com.sun.xml.bind.v2.runtime.reflect.opt.Const;
import java.util.Random;
import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;

import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

import static org.keycloak.utils.IdentityProviderUtils.splitNames;
import static org.keycloak.utils.IdentityProviderUtils.stripMobilePhoneNumber;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class RegistrationProfile implements FormAction, FormActionFactory {
    public static final String PROVIDER_ID = "registration-profile-action";
    private static final Logger logger = Logger.getLogger(RegistrationProfile.class);

    public static void populateLastNameFirstNameUsingName(MultivaluedMap<String, String> formData) {
        String inputName = formData.getFirst(Validation.FIELD_NAME);
        if (!Validation.isBlank(inputName)) {
            List<String> splittedName = splitNames(inputName);
            if (splittedName.size() > 0) {
                formData.putSingle(Validation.FIELD_FIRST_NAME, splittedName.get(0));
                if (splittedName.size() > 1) {
                    formData.putSingle(Validation.FIELD_LAST_NAME, splittedName.get(1));
                }
            }
        }
    }


    public static void populateReferredByCode(HttpRequest httpRequest, MultivaluedMap<String, String> formData) {
        Object referralCodeObject = httpRequest.getAttribute(Constants.REFERRAL_CODE);
        String referralCode = referralCodeObject != null ? referralCodeObject.toString() : null;
        if (referralCode != null){
            formData.add(Validation.FIELD_REFERRED_BY_CODE, referralCode);
        }
    }

    public static void populateFormFields(HttpRequest httpRequest, IUser user, MultivaluedMap<String, String> formData) {
        populateLastNameFirstNameUsingName(formData);
        populateReferredByCode(httpRequest, formData);
        user.setFirstName(formData.getFirst(Validation.FIELD_FIRST_NAME));
        user.setLastName(formData.getFirst(Validation.FIELD_LAST_NAME));
        user.setEmail(formData.getFirst(Validation.FIELD_EMAIL));

        populateAttributes(user, formData);
    }

    public static String generateReferralCode() {
        int leftLimit = 48; // numeral '0'
//        int rightLimit = 122; // letter 'z'
        int rightLimit = 90; // letter 'Z'
        int targetStringLength = 6;
        Random random = new Random();

        String generatedString = random.ints(leftLimit, rightLimit + 1)
            .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
            .limit(targetStringLength)
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();

        return generatedString.toUpperCase();
    }

    public static void populateAttributes(IUser user, MultivaluedMap<String, String> formData) {
        String mobilePhoneNumber = formData.getFirst(Validation.FIELD_MOBILE_PHONE_NUMBER);
        mobilePhoneNumber = stripMobilePhoneNumber(mobilePhoneNumber);
        if (mobilePhoneNumber != null) {
            user.setSingleAttribute(Validation.FIELD_MOBILE_PHONE_NUMBER, mobilePhoneNumber);
        }
        String company = formData.getFirst(Validation.FIELD_COMPANY);
        if (company != null && !company.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_COMPANY, company);
        }

        String birthDate = formData.getFirst(Validation.FIELD_BIRTH_DATE);
        if (birthDate != null && !birthDate.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_BIRTH_DATE, birthDate);
        }
        String profileImageUrl = formData.getFirst(Validation.FIELD_PROFILE_IMAGE_URL);
        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_PROFILE_IMAGE_URL, profileImageUrl);
        }
        String profileThumbnailImageUrl = formData.getFirst(Validation.FIELD_PROFILE_THUMBNAIL_IMAGE_URL);
        if (profileThumbnailImageUrl != null && !profileThumbnailImageUrl.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_PROFILE_THUMBNAIL_IMAGE_URL, profileThumbnailImageUrl);
        }
        String genderCode = formData.getFirst(Validation.FIELD_GENDER_CODE);
        if (genderCode != null && !genderCode.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_GENDER_CODE, genderCode);
        }
        String referredByCode = formData.getFirst(Validation.FIELD_REFERRED_BY_CODE);
        if (referredByCode != null){
            referredByCode = referredByCode.trim();
            if (!referredByCode.isEmpty()){
                user.setSingleAttribute(Validation.FIELD_REFERRED_BY_CODE, referredByCode.toUpperCase());
            }
        }
        String currentLocale = user.getFirstAttribute(Validation.FIELD_LOCALE);
        if (currentLocale == null || currentLocale.isEmpty()) {
            user.setSingleAttribute(Validation.FIELD_LOCALE, "ko");
        }
        String referralCode = generateReferralCode();
        user.setSingleAttribute(Validation.FIELD_REFERRAL_CODE, referralCode);
        user.setSingleAttribute(Validation.FIELD_SERVICE_AGREEMENT, getBooleanValue(formData, Validation.FIELD_SERVICE_AGREEMENT));
        user.setSingleAttribute(Validation.FIELD_PRIVACY_AGREEMENT, getBooleanValue(formData, Validation.FIELD_PRIVACY_AGREEMENT));
        user.setSingleAttribute(Validation.FIELD_MARKETING_AGREEMENT, getBooleanValue(formData, Validation.FIELD_MARKETING_AGREEMENT));
    }

    @Override
    public String getHelpText() {
        return "Validates email, first name, and last name attributes and stores them in user data.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        List<FormMessage> errors = new ArrayList<>();

        context.getEvent().detail(Details.REGISTER_METHOD, "form");
        String eventError = Errors.INVALID_REGISTRATION;

        Validation.validateProfileForm(formData, errors, false);
        String email = formData.getFirst(Validation.FIELD_EMAIL);
//        String password = formData.getFirst(Validation.FIELD_PASSWORD);
//        String name = formData.getFirst(Validation.FIELD_NAME);
//        String mobilePhoneNumber = formData.getFirst(Validation.FIELD_MOBILE_PHONE_NUMBER);

        boolean emailValid = true;
        if (Validation.isBlank(email)) {
            errors.add(new FormMessage(Validation.FIELD_EMAIL, Messages.MISSING_EMAIL));
            emailValid = false;
        } else if (!Validation.isEmailValid(email)) {
            context.getEvent().detail(Details.EMAIL, email);
            errors.add(new FormMessage(Validation.FIELD_EMAIL, Messages.INVALID_EMAIL));
            emailValid = false;
        }

        if (emailValid && !context.getRealm().isDuplicateEmailsAllowed() && context.getSession().users().getUserByEmail(email, context.getRealm()) != null) {
            eventError = Errors.EMAIL_IN_USE;
//            formData.remove(Validation.FIELD_EMAIL);
            context.getEvent().detail(Details.EMAIL, email);
            errors.add(new FormMessage(Validation.FIELD_EMAIL, Messages.EMAIL_EXISTS));
        }

        if (errors.size() > 0) {
            context.error(eventError);
            context.validationError(formData, errors);
            return;

        } else {
            context.success();
        }
    }

    public static String getBooleanValue(MultivaluedMap<String, String> formData, String propertyName) {
        String value = formData.getFirst(propertyName);
        if (value == null || value.isEmpty()) {
            value = "false";
        }
        return value;
    }

    @Override
    public void success(FormContext context) {
        UserModel user = context.getUser();
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        populateFormFields(context.getHttpRequest(), user, formData);
        logger.info(user.toString());
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        // complete
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }


    @Override
    public void close() {

    }

    @Override
    public String getDisplayType() {
        return "Profile Validation";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    private static AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
        AuthenticationExecutionModel.Requirement.REQUIRED,
        AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
