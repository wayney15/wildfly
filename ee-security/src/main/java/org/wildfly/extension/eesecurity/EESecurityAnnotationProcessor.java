/*
 * Copyright (C) 2018 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.wildfly.extension.eesecurity;

import java.util.List;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.annotation.CompositeIndex;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;

class EESecurityAnnotationProcessor implements DeploymentUnitProcessor {

    static final AttachmentKey<Boolean> SECURITY_PRESENT = AttachmentKey.create(Boolean.class);

    static final DotName[] ANNOTATIONS = {
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.BasicAuthenticationMechanismDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.CustomFormAuthenticationMechanismDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.FormAuthenticationMechanismDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.OpenIdAuthenticationMechanismDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.LoginToContinue"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.RememberMe"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.AutoApplySession"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.openid.ClaimsDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.openid.LogoutDefinition"),
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.openid.OpenIdProviderMetadata"),
            DotName.createSimple("jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition"),
            DotName.createSimple("jakarta.security.enterprise.identitystore.LdapIdentityStoreDefinition")
    };

    static final DotName[] INTERFACES = {
            DotName.createSimple("jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism"),
            DotName.createSimple("jakarta.security.enterprise.identitystore.IdentityStoreHandler"),
            DotName.createSimple("jakarta.security.enterprise.identitystore.IdentityStore"),
            DotName.createSimple("jakarta.security.enterprise.identitystore.RememberMeIdentityStore")
    };

    static final DotName INJECTION_TYPE = DotName.createSimple("jakarta.inject.Inject");

    static final DotName SECURITY_CONTEXT = DotName.createSimple("jakarta.security.enterprise.SecurityContext");

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        CompositeIndex index = deploymentUnit.getAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        for (DotName annotation : ANNOTATIONS) {
            if (!index.getAnnotations(annotation).isEmpty()) {
                markAsEESecurity(deploymentUnit);
                return;
            }
        }
        for (DotName annotation : INTERFACES) {
            if (!index.getAllKnownImplementors(annotation).isEmpty()) {
                markAsEESecurity(deploymentUnit);
                return;
            }
        }

        // Get list of injected objects
        List<AnnotationInstance> injectionAnnotations = index.getAnnotations(INJECTION_TYPE);
        for (AnnotationInstance annotation : injectionAnnotations) {
            final AnnotationTarget annotationTarget = annotation.target();
            if (annotationTarget instanceof FieldInfo) {
                // Enable if injected object is a SecurityContext field, WFLY-17541
                final FieldInfo fieldInfo = (FieldInfo) annotationTarget;
                DotName injectedFieldName = fieldInfo.type().name();
                if (injectedFieldName.equals(SECURITY_CONTEXT)) {
                    markAsEESecurity(deploymentUnit);
                    return;
                }
            }
        }
    }

    private void markAsEESecurity(DeploymentUnit deploymentUnit) {
        DeploymentUnit top = deploymentUnit.getParent() == null ? deploymentUnit : deploymentUnit.getParent();
        top.putAttachment(SECURITY_PRESENT, true);
    }
}
