/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.security.javaeesec.identitystore;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import javax.el.ELException;
import javax.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import javax.security.enterprise.identitystore.IdentityStore;
import javax.security.enterprise.identitystore.IdentityStore.ValidationType;
import javax.security.enterprise.identitystore.LdapIdentityStoreDefinition;
import javax.security.enterprise.identitystore.LdapIdentityStoreDefinition.LdapSearchScope;

import com.ibm.websphere.ras.Tr;
import com.ibm.websphere.ras.TraceComponent;
import com.ibm.websphere.ras.annotation.Trivial;
import com.ibm.ws.ffdc.annotation.FFDCIgnore;
import com.ibm.ws.security.javaeesec.CDIHelper;

/**
 * Class to help with evaulating EL expressions for identity stores.
 */
class ELHelper {
    private static final String OBFUSCATED_STRING = "******";
    private static final TraceComponent tc = Tr.register(ELHelper.class);

    /**
     * Evaluate a possible EL expression.
     *
     * @param expression The expression to evaluate.
     * @return The evaluated expression.
     */
    @Trivial
    static Object evaluateElExpression(String expression) {
        return evaluateElExpression(expression, false);
    }

    /**
     * Evaluate a possible EL expression.
     *
     * @param expression The expression to evaluate.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might be
     *            contained in either the expression or the result.
     * @return The evaluated expression.
     */
    @Trivial
    static Object evaluateElExpression(String expression, boolean mask) {
        final String methodName = "evaluateElExpression";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, mask });
        }

        Object result = CDIHelper.getELProcessor().eval(removeBrackets(expression, mask));

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, (result == null) ? null : mask ? OBFUSCATED_STRING : result);
        }
        return result;
    }

    /**
     * Return whether the expression is an immediate EL expression.
     *
     * @param expression The expression to evaluate.
     * @return True if the expression is an immediate EL expression.
     */
    @Trivial
    static boolean isImmediateExpression(String expression) {
        return isImmediateExpression(expression, false);
    }

    /**
     * Return whether the expression is an immediate EL expression.
     *
     * @param expression The expression to evaluate.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might be
     *            contained in either the expression or the result.
     * @return True if the expression is an immediate EL expression.
     */
    @Trivial
    static boolean isImmediateExpression(String expression, boolean mask) {
        final String methodName = "isImmediateExpression";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, mask });
        }

        boolean result = expression.startsWith("${") && expression.endsWith("}");

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, result);
        }
        return result;
    }

    /**
     * This method will process a configuration value for an Integer setting in
     * {@link LdapIdentityStoreDefinition} or {@link DatabaseIdentityStoreDefinition}.
     * It will first check to see if there is an EL expression. It there is, it will return
     * the evaluated expression; otherwise, it
     * will return the non-EL value.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The EL expression returned from from the identity store definition.
     * @param value The non-EL value.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @return Either the evaluated EL expression or the non-EL value.
     */
    static Integer processInt(String name, String expression, int value, boolean immediateOnly) {
        Integer result = null;
        boolean immediate = false;

        /*
         * The expression language value takes precedence over the direct setting.
         */
        if (expression.isEmpty()) {
            /*
             * Direct setting.
             */
            result = value;
        } else {
            /*
             * Evaluate the EL expression to get the value.
             */
            Object obj = evaluateElExpression(expression);
            if (obj == null) {
                throw new IllegalArgumentException("EL expression '" + expression + "' for '" + name + "'evaluated to null.");
            } else if (obj instanceof Number) {
                result = ((Number) obj).intValue();
                immediate = isImmediateExpression(expression);
            } else {
                throw new IllegalArgumentException("Expected '" + name + "' to evaluate to an integer value.");
            }
        }

        return (immediateOnly && !immediate) ? null : result;
    }

    /**
     * This method will process a configuration value for LdapSearchScope setting in
     * {@link LdapIdentityStoreDefinition}. It will first check to see if there is an
     * EL expression. It there is, it will return the evaluated expression; otherwise, it
     * will return the non-EL value.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The EL expression returned from from the identity store definition.
     * @param value The non-EL value.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @return Either the evaluated EL expression or the non-EL value.
     */
    static LdapSearchScope processLdapSearchScope(String name, String expression, LdapSearchScope value, boolean immediateOnly) {
        LdapSearchScope result;
        boolean immediate = false;

        /*
         * The expression language value takes precedence over the direct setting.
         */
        if (expression.isEmpty()) {
            /*
             * Direct setting.
             */
            result = value;
        } else {
            /*
             * Evaluate the EL expression to get the value.
             */
            Object obj = evaluateElExpression(expression);
            if (obj instanceof LdapSearchScope) {
                result = (LdapSearchScope) obj;
                immediate = isImmediateExpression(expression);
            } else {
                throw new IllegalArgumentException("Expected '" + name + "' to evaluate to an LdapSearchScope type.");
            }
        }

        return (immediateOnly && !immediate) ? null : result;
    }

    /**
     * This method will process a configuration value for any configuration setting in
     * {@link LdapIdentityStoreDefinition} or {@link DatabaseIdentityStoreDefinition} that
     * is a string and whose name is NOT a "*Expression". It will first check to see if it
     * is a EL expression. It it is, it will return the evaluated expression; otherwise, it
     * will return the literal String.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The value returned from from the identity store definition, which can
     *            either be a literal String or an EL expression.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @return The String value.
     */
    @Trivial
    static String processString(String name, String expression, boolean immediateOnly) {
        return processString(name, expression, immediateOnly, false);
    }

    /**
     * This method will process a configuration value for any configuration setting in
     * {@link LdapIdentityStoreDefinition} or {@link DatabaseIdentityStoreDefinition} that
     * is a string and whose name is NOT a "*Expression". It will first check to see if it
     * is a EL expression. It it is, it will return the evaluated expression; otherwise, it
     * will return the literal String.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The value returned from from the identity store definition, which can
     *            either be a literal String or an EL expression.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might
     *            be contained in either the expression or the result.
     * @return The String value.
     */
    @FFDCIgnore(ELException.class)
    @Trivial
    static String processString(String name, String expression, boolean immediateOnly, boolean mask) {

        final String methodName = "processString";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { name, (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, immediateOnly, mask });
        }

        String result;
        boolean immediate = false;

        try {
            Object obj = evaluateElExpression(expression, mask);
            if (obj == null) {
                throw new IllegalArgumentException("EL expression '" + (mask ? OBFUSCATED_STRING : expression) + "' for '" + name + "'evaluated to null.");
            } else if (obj instanceof String) {
                result = (String) obj;
                immediate = isImmediateExpression(expression, mask);
            } else {
                throw new IllegalArgumentException("Expected '" + name + "' to evaluate to a String value.");
            }
        } catch (ELException e) {
            result = expression;
            immediate = true;
        }

        String finalResult = (immediateOnly && !immediate) ? null : result;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, (finalResult == null) ? null : mask ? OBFUSCATED_STRING : finalResult);
        }

        return finalResult;
    }

    /**
     * This method will process a configuration value for an String[] setting in
     * {@link LdapIdentityStoreDefinition} or {@link DatabaseIdentityStoreDefinition}.
     * It will first check to see if there is an EL expression. It there is, it will return
     * the evaluated expression; otherwise, it will return the non-EL value.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The EL expression returned from from the identity store definition.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might be
     *            contained in either the expression or the result.
     * @return Either the evaluated EL expression or the non-EL value.
     */
    @FFDCIgnore(ELException.class)
    @Trivial
    static String[] processStringArray(String name, String expression, boolean immediateOnly, boolean mask) {

        final String methodName = "processStringArray";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { name, (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, immediateOnly, mask });
        }

        String[] result;
        boolean immediate = false;

        try {
            Object obj = evaluateElExpression(expression, mask);
            if (obj == null) {
                throw new IllegalArgumentException("EL expression '" + (mask ? OBFUSCATED_STRING : expression) + "' for '" + name + "'evaluated to null.");
            } else if (obj instanceof String[]) {
                result = (String[]) obj;
                immediate = isImmediateExpression(expression, mask);
            } else {
                throw new IllegalArgumentException("Expected '" + name + "' to evaluate to a String value.");
            }
        } catch (ELException e) {
            throw new IllegalArgumentException("Expected '" + name + "' to evaluate to a String[] value.");
        }

        String[] finalResult = (immediateOnly && !immediate) ? null : result;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, (finalResult == null) ? null : mask ? OBFUSCATED_STRING : finalResult);
        }

        return finalResult;
    }

    /**
     * This method will process a configuration value for an String[] setting in
     * {@link LdapIdentityStoreDefinition} or {@link DatabaseIdentityStoreDefinition}.
     * It will first check to see if there is an EL expression. It there is, it will return
     * the evaluated expression; otherwise, it will return the non-EL value.
     *
     * @param name The name of the property. Used for error messages.
     * @param expression The EL expression returned from from the identity store definition.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might be
     *            contained in either the expression or the result.
     * @return Either the evaluated EL expression or the non-EL value.
     */
    @FFDCIgnore(ELException.class)
    @Trivial
    static Stream<String> processStringStream(String name, String expression, boolean immediateOnly, boolean mask) {

        final String methodName = "processStringStream";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { name, (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, immediateOnly, mask });
        }

        Stream<String> result;
        boolean immediate = false;

        try {
            Object obj = evaluateElExpression(expression, mask);
            if (obj == null) {
                throw new IllegalArgumentException("EL expression '" + (mask ? OBFUSCATED_STRING : expression) + "' for '" + name + "'evaluated to null.");
            } else if (obj instanceof Stream) {
                result = (Stream<String>) obj;
                immediate = isImmediateExpression(expression, mask);
            } else {
                throw new IllegalArgumentException("Expected '" + name + "' to evaluate to a Stream<String> value.");
            }
        } catch (ELException e) {
            throw new IllegalArgumentException("Expected '" + name + "' to evaluate to a Stream<String> value.");
        }

        Stream<String> finalResult = (immediateOnly && !immediate) ? null : result;
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, (finalResult == null) ? null : mask ? OBFUSCATED_STRING : finalResult);
        }

        return finalResult;
    }

    /**
     * Validate and return the {@link ValidationType}s for the {@link IdentityStore} from either
     * the EL expression or the direct useFor setting.
     *
     * @param useForExpression The EL expression returned from from the identity store definition.
     * @param useFor The non-EL value.
     * @param immediateOnly Return null if the value is a deferred EL expression.
     *
     * @return The validated useFor types.
     */
    static Set<ValidationType> processUseFor(String useForExpression, ValidationType[] useFor, boolean immediateOnly) {
        Set<ValidationType> result = null;
        boolean immediate = false;

        /*
         * The expression language value takes precedence over the direct setting.
         */
        if (useForExpression.isEmpty()) {
            result = new HashSet<ValidationType>(Arrays.asList(useFor));
        } else {
            /*
             * Evaluate the EL expression to get the value.
             */
            Object obj = evaluateElExpression(useForExpression);
            if (obj instanceof ValidationType[]) {
                ValidationType[] types = (ValidationType[])obj;
                result = new HashSet<ValidationType>(Arrays.asList(types));
                immediate = isImmediateExpression(useForExpression);
            } else if (obj instanceof Set) {
                result = (Set<ValidationType>) obj;
                immediate = isImmediateExpression(useForExpression);
            } else {
                throw new IllegalArgumentException("Expected 'useForExpression' to evaluate to a Set<ValidationType>.");
            }
        }

        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("The identity store must be configured with at least one ValidationType.");
        }
        return (immediateOnly && !immediate) ? null : Collections.unmodifiableSet(result);
    }

    /**
     * Remove the brackets from an EL expression.
     *
     * @param expression The expression to remove the brackets from.
     * @return The EL expression without the brackets.
     */
    @Trivial
    static String removeBrackets(String expression) {
        return removeBrackets(expression, false);
    }

    /**
     * Remove the brackets from an EL expression.
     *
     * @param expression The expression to remove the brackets from.
     * @param mask Set whether to mask the expression and result. Useful for when passwords might
     *            be contained in either the expression or the result.
     * @return The EL expression without the brackets.
     */
    @Trivial
    static String removeBrackets(String expression, boolean mask) {
        final String methodName = "removeBrackets";
        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.entry(tc, methodName, new Object[] { (expression == null) ? null : mask ? OBFUSCATED_STRING : expression, mask });
        }

        expression = expression.trim();
        if ((expression.startsWith("${") || expression.startsWith("#{")) && expression.endsWith("}")) {
            expression = expression.substring(2, expression.length() - 1);
        }

        if (TraceComponent.isAnyTracingEnabled() && tc.isEntryEnabled()) {
            Tr.exit(tc, methodName, (expression == null) ? null : mask ? OBFUSCATED_STRING : expression);
        }
        return expression;
    }
}
