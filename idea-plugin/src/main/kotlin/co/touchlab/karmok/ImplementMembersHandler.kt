/*
 * Copyright (c) 2020 Touchlab
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package co.touchlab.karmok

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.project.implementedDescriptors
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.js.descriptorUtils.hasPrimaryConstructor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.resolve.OverrideResolver
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.multiplatform.ExpectedActualResolver

open class ImplementMembersHandler : OverrideImplementMembersHandler(), IntentionAction {
    override fun collectMembersToGenerate(descriptor: ClassDescriptor, project: Project): Collection<OverrideMemberChooserObject> {
        return OverrideResolver.getMissingImplementations(descriptor)
            .map { OverrideMemberChooserObject.create(project, it, it, OverrideMemberChooserObject.BodyType.FROM_TEMPLATE) }
    }

    override fun getChooserTitle() = "Implement Members"

    override fun getNoMembersFoundHint() = "No members to implement have been found"

    override fun getText() = KotlinBundle.message("implement.members")
    override fun getFamilyName() = KotlinBundle.message("implement.members")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = isValidFor(editor, file)
}

class ImplementAsConstructorParameter : ImplementMembersHandler() {
    override fun getText() = "Implement as constructor parameters"

    override fun isValidForClass(classOrObject: KtClassOrObject): Boolean {
        if (classOrObject !is KtClass || classOrObject is KtEnumEntry || classOrObject.isInterface()) return false
        val classDescriptor = classOrObject.resolveToDescriptorIfAny() ?: return false
        if (classDescriptor.isActual) {
            if (classDescriptor.expectedDescriptors().any {
                    it is ClassDescriptor && it.hasPrimaryConstructor()
                }
            ) {
                return false
            }
        }
        return OverrideResolver.getMissingImplementations(classDescriptor).any { it is PropertyDescriptor }
    }

    override fun collectMembersToGenerate(descriptor: ClassDescriptor, project: Project): Collection<OverrideMemberChooserObject> {
        return OverrideResolver.getMissingImplementations(descriptor)
            .filter { it is PropertyDescriptor }
            .map { OverrideMemberChooserObject.create(project, it, it, OverrideMemberChooserObject.BodyType.FROM_TEMPLATE, true) }
    }
}

internal fun MemberDescriptor.expectedDescriptors() = module.implementedDescriptors.mapNotNull { it.declarationOf(this) }
private fun ModuleDescriptor.declarationOf(descriptor: MemberDescriptor): DeclarationDescriptor? =
    with(ExpectedActualResolver) {
        val expectedCompatibilityMap = findExpectedForActual(descriptor, this@declarationOf)
        expectedCompatibilityMap?.get(ExpectedActualResolver.Compatibility.Compatible)?.firstOrNull()
            ?: expectedCompatibilityMap?.values?.flatten()?.firstOrNull()
    }