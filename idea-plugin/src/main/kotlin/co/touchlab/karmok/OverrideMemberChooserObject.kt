package co.touchlab.karmok

import co.touchlab.karmok.OverrideMemberChooserObject.BodyType.*
import com.intellij.codeInsight.generation.ClassMember
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.TemplateKind
import org.jetbrains.kotlin.idea.core.getFunctionBodyTextFromTemplate
import org.jetbrains.kotlin.idea.core.util.DescriptorMemberChooserObject
import org.jetbrains.kotlin.idea.j2k.IdeaDocCommentConverter
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.approximateFlexibleTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.findDocComment.findDocComment
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.hasExpectModifier
import org.jetbrains.kotlin.renderer.*
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.checkers.ExperimentalUsageChecker
import org.jetbrains.kotlin.resolve.descriptorUtil.setSingleOverridden
import org.jetbrains.kotlin.util.findCallableMemberBySignature

interface OverrideMemberChooserObject : ClassMember {
    sealed class BodyType(val requiresReturn: Boolean = true) {
        object NO_BODY : BodyType()
        object EMPTY_OR_TEMPLATE : BodyType(requiresReturn = false)
        object FROM_TEMPLATE : BodyType(requiresReturn = false)
        object SUPER : BodyType()
        object QUALIFIED_SUPER : BodyType()

        class Delegate(val receiverName: String) : BodyType()
    }

    val descriptor: CallableMemberDescriptor
    val immediateSuper: CallableMemberDescriptor
    val bodyType: BodyType
    val preferConstructorParameter: Boolean

    companion object {
        fun create(
            project: Project,
            descriptor: CallableMemberDescriptor,
            immediateSuper: CallableMemberDescriptor,
            bodyType: BodyType,
            preferConstructorParameter: Boolean = false
        ): OverrideMemberChooserObject {
            val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)
            return if (declaration != null) {
                create(declaration, descriptor, immediateSuper, bodyType, preferConstructorParameter)
            } else {
                WithoutDeclaration(descriptor, immediateSuper, bodyType, preferConstructorParameter)
            }
        }

        fun create(
            declaration: PsiElement,
            descriptor: CallableMemberDescriptor,
            immediateSuper: CallableMemberDescriptor,
            bodyType: BodyType,
            preferConstructorParameter: Boolean = false
        ): OverrideMemberChooserObject =
            WithDeclaration(descriptor, declaration, immediateSuper, bodyType, preferConstructorParameter)

        private class WithDeclaration(
            descriptor: CallableMemberDescriptor,
            declaration: PsiElement,
            override val immediateSuper: CallableMemberDescriptor,
            override val bodyType: BodyType,
            override val preferConstructorParameter: Boolean
        ) : DescriptorMemberChooserObject(declaration, descriptor), OverrideMemberChooserObject {

            override val descriptor: CallableMemberDescriptor
                get() = super.descriptor as CallableMemberDescriptor
        }

        private class WithoutDeclaration(
            override val descriptor: CallableMemberDescriptor,
            override val immediateSuper: CallableMemberDescriptor,
            override val bodyType: BodyType,
            override val preferConstructorParameter: Boolean
        ) : MemberChooserObjectBase(
            DescriptorMemberChooserObject.getText(descriptor), DescriptorMemberChooserObject.getIcon(null, descriptor)
        ), OverrideMemberChooserObject {

            override fun getParentNodeDelegate(): MemberChooserObject? {
                val parentClassifier = descriptor.containingDeclaration as? ClassifierDescriptor ?: return null
                return MemberChooserObjectBase(
                    DescriptorMemberChooserObject.getText(parentClassifier), DescriptorMemberChooserObject.getIcon(null, parentClassifier)
                )
            }
        }
    }
}

enum class MemberGenerateMode {
    OVERRIDE,
    ACTUAL,
    EXPECT
}

fun OverrideMemberChooserObject.generateMember(
    targetClass: KtClassOrObject,
    copyDoc: Boolean,
    nameMap: MockNameMap
) = generateMember(targetClass, copyDoc, targetClass.project, mode = MemberGenerateMode.OVERRIDE, nameMap = nameMap)

fun OverrideMemberChooserObject.generateMocker(
    targetClass: KtClassOrObject,
    nameMap: MockNameMap
) : KtDeclaration {
    val factory = KtPsiFactory(targetClass.project)
    val typeBuilder = StringBuilder(targetClass.fqName?.asString())
    val typeParameters = targetClass.typeParameters
    if(typeParameters.isNotEmpty()){
        typeBuilder.append("<${typeParameters.joinToString { it.name!! }}>")
    }
    val targetTypestring = typeBuilder.toString()

    val propertyDefinition: String = when (descriptor) {
        is FunctionDescriptor -> "internal val ${nameMap.getName(this)} = MockFunctionRecorder<$targetTypestring, ${descriptor.returnType.toString()}>()"
        is PropertyDescriptor -> {
            val propSetter = if((descriptor as PropertyDescriptor).isVar){"${descriptor.name} = it"}else{""}
            "internal val ${nameMap.getName(this)} = MockPropertyRecorder<$targetTypestring, ${descriptor.returnType.toString()}>({${descriptor.name}}) {$propSetter}"
        }
        else -> error("Unknown member to override: $descriptor")
    }
    return factory.createProperty(propertyDefinition)
}

fun OverrideMemberChooserObject.generateMember(
    targetClass: KtClassOrObject?,
    copyDoc: Boolean,
    project: Project,
    mode: MemberGenerateMode,
    nameMap: MockNameMap
): KtCallableDeclaration {
    val descriptor = immediateSuper

    val bodyType = when {
        targetClass?.hasExpectModifier() == true -> NO_BODY
        descriptor.extensionReceiverParameter != null && mode == MemberGenerateMode.OVERRIDE -> FROM_TEMPLATE
        else -> bodyType
    }

    val baseRenderer = when (mode) {
        MemberGenerateMode.OVERRIDE -> OVERRIDE_RENDERER
        MemberGenerateMode.ACTUAL -> ACTUAL_RENDERER
        MemberGenerateMode.EXPECT -> EXPECT_RENDERER
    }
    val renderer = baseRenderer.withOptions {
        if (descriptor is ClassConstructorDescriptor && descriptor.isPrimary) {
            val containingClass = descriptor.containingDeclaration
            if (containingClass.kind == ClassKind.ANNOTATION_CLASS || containingClass.isInline) {
                renderPrimaryConstructorParametersAsProperties = true
            }
        }
    }

    if (preferConstructorParameter && descriptor is PropertyDescriptor) {
        return generateConstructorParameter(project, descriptor, renderer, mode == MemberGenerateMode.OVERRIDE)
    }

    val mockName = nameMap.getName(this)

    val newMember: KtCallableDeclaration = when (descriptor) {
        is FunctionDescriptor -> generateFunction(project, descriptor, renderer, bodyType, mockName, mode == MemberGenerateMode.OVERRIDE)
        is PropertyDescriptor -> generateProperty(project, descriptor, renderer, bodyType, mockName, mode == MemberGenerateMode.OVERRIDE)
        else -> error("Unknown member to override: $descriptor")
    }

    when (mode) {
        MemberGenerateMode.ACTUAL -> newMember.addModifier(KtTokens.ACTUAL_KEYWORD)
        MemberGenerateMode.EXPECT -> if (targetClass == null) {
            newMember.addModifier(KtTokens.EXPECT_KEYWORD)
        }
        MemberGenerateMode.OVERRIDE -> {
            if (targetClass?.hasActualModifier() == true) {
                val expectClassDescriptors =
                    targetClass.resolveToDescriptorIfAny()?.expectedDescriptors()?.filterIsInstance<ClassDescriptor>().orEmpty()
                if (expectClassDescriptors.any { expectClassDescriptor ->
                        val expectMemberDescriptor = expectClassDescriptor.findCallableMemberBySignature(immediateSuper)
                        expectMemberDescriptor?.isExpect == true && expectMemberDescriptor.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
                    }
                ) {
                    newMember.addModifier(KtTokens.ACTUAL_KEYWORD)
                }
            }
        }
    }

    if (copyDoc) {
        val superDeclaration = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptor)?.navigationElement
        val kDoc = when (superDeclaration) {
            is KtDeclaration ->
                findDocComment(superDeclaration)
            is PsiDocCommentOwner -> {
                val kDocText = superDeclaration.docComment?.let { IdeaDocCommentConverter.convertDocComment(it) }
                if (kDocText.isNullOrEmpty()) null else KDocElementFactory(project).createKDocFromText(kDocText)
            }
            else -> null
        }
        if (kDoc != null) {
            newMember.addAfter(kDoc, null)
        }
    }

    return newMember
}

private val OVERRIDE_RENDERER = DescriptorRenderer.withOptions {
    defaultParameterValueRenderer = null
    modifiers = setOf(DescriptorRendererModifier.OVERRIDE, DescriptorRendererModifier.ANNOTATIONS)
    withDefinedIn = false
    classifierNamePolicy = ClassifierNamePolicy.SOURCE_CODE_QUALIFIED
    overrideRenderingPolicy = OverrideRenderingPolicy.RENDER_OVERRIDE
    unitReturnType = false
    enhancedTypes = true
    typeNormalizer = IdeDescriptorRenderers.APPROXIMATE_FLEXIBLE_TYPES
    renderUnabbreviatedType = false
    annotationFilter = {
        it.type.constructor.declarationDescriptor?.annotations?.hasAnnotation(ExperimentalUsageChecker.EXPERIMENTAL_FQ_NAME)
            ?: false
    }
    presentableUnresolvedTypes = true
}

private val EXPECT_RENDERER = OVERRIDE_RENDERER.withOptions {
    modifiers = setOf(
        DescriptorRendererModifier.VISIBILITY,
        DescriptorRendererModifier.MODALITY,
        DescriptorRendererModifier.OVERRIDE,
        DescriptorRendererModifier.INNER,
        DescriptorRendererModifier.MEMBER_KIND
    )
    renderConstructorKeyword = false
    secondaryConstructorsAsPrimary = false
    renderDefaultVisibility = false
    renderDefaultModality = false
    renderTypeExpansions = true
}

private val ACTUAL_RENDERER = EXPECT_RENDERER.withOptions {
    modifiers += DescriptorRendererModifier.ACTUAL
    actualPropertiesInPrimaryConstructor = true
    renderTypeExpansions = false
    renderConstructorDelegation = true
}

private fun PropertyDescriptor.wrap(forceOverride: Boolean): PropertyDescriptor {
    val delegate = copy(containingDeclaration, if (forceOverride) Modality.OPEN else modality, visibility, kind, true) as PropertyDescriptor
    val newDescriptor = object : PropertyDescriptor by delegate {
        override fun isExpect() = false
    }
    if (forceOverride) {
        newDescriptor.setSingleOverridden(this)
    }
    return newDescriptor
}

private fun FunctionDescriptor.wrap(forceOverride: Boolean): FunctionDescriptor {
    if (this is ClassConstructorDescriptor) return this.wrap()
    return object : FunctionDescriptor by this {
        override fun isExpect() = false
        override fun getModality() = if (forceOverride) Modality.OPEN else this@wrap.modality
        override fun getReturnType() = this@wrap.returnType?.approximateFlexibleTypes(preferNotNull = true, preferStarForRaw = true)
        override fun getOverriddenDescriptors() = if (forceOverride) listOf(this@wrap) else this@wrap.overriddenDescriptors
        override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D) =
            visitor.visitFunctionDescriptor(this, data)
    }
}

private fun ClassConstructorDescriptor.wrap(): ClassConstructorDescriptor {
    return object : ClassConstructorDescriptor by this {
        override fun isExpect() = false
        override fun getModality() = Modality.FINAL
        override fun getReturnType() = this@wrap.returnType.approximateFlexibleTypes(preferNotNull = true, preferStarForRaw = true)
        override fun getOverriddenDescriptors(): List<ClassConstructorDescriptor> = emptyList()
        override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>, data: D) =
            visitor.visitConstructorDescriptor(this, data)
    }
}

private fun generateProperty(
    project: Project,
    descriptor: PropertyDescriptor,
    renderer: DescriptorRenderer,
    bodyType: OverrideMemberChooserObject.BodyType,
    name: String,
    forceOverride: Boolean
): KtProperty {
    val newDescriptor = descriptor.wrap(forceOverride)
    val body =
        if (bodyType != NO_BODY) {
            buildString {
                append(" by mock.$name")
            }
        } else ""
    return KtPsiFactory(project).createProperty(renderer.render(newDescriptor) + body)
}

private fun generateConstructorParameter(
    project: Project,
    descriptor: PropertyDescriptor,
    renderer: DescriptorRenderer,
    forceOverride: Boolean
): KtParameter {
    val newDescriptor = descriptor.wrap(forceOverride)
    newDescriptor.setSingleOverridden(descriptor)
    return KtPsiFactory(project).createParameter(renderer.render(newDescriptor))
}

private fun generateFunction(
    project: Project,
    descriptor: FunctionDescriptor,
    renderer: DescriptorRenderer,
    bodyType: OverrideMemberChooserObject.BodyType,
    name: String,
    forceOverride: Boolean
): KtFunction {
    val newDescriptor = descriptor.wrap(forceOverride)

    val returnType = descriptor.returnType
    val returnsNotUnit = returnType != null && !KotlinBuiltIns.isUnit(returnType)

    val body = if (bodyType != NO_BODY) {
        val paramsString = descriptor.valueParameters.joinToString {
            val sb = StringBuilder()
            if(it.isVararg)
                sb.append("*")
            sb.append(it.name.asString())
            sb.toString()
        }
        val invokeName = if(returnsNotUnit){"invoke"}else{"invokeUnit"}
        val accessMethod = "${descriptor.name.asString()}($paramsString)"//if(returnsNotUnit){"${descriptor.name.asString()}($paramsString)"}else{""}
        val delegation = "mock.${name}.${invokeName}({$accessMethod}, listOf(${paramsString}))"
        val returnPrefix = if (returnsNotUnit) "return " else ""
        "{$returnPrefix$delegation\n}"
    } else ""

    val factory = KtPsiFactory(project)
    val functionText = renderer.render(newDescriptor) + body
    return when (descriptor) {
        is ClassConstructorDescriptor -> {
            if (descriptor.isPrimary) {
                factory.createPrimaryConstructor(functionText)
            } else {
                factory.createSecondaryConstructor(functionText)
            }
        }
        else -> factory.createFunction(functionText)
    }
}



fun generateUnsupportedOrSuperCall(
    project: Project,
    descriptor: CallableMemberDescriptor,
    bodyType: OverrideMemberChooserObject.BodyType,
    canBeEmpty: Boolean = true
): String {
    val effectiveBodyType = if (!canBeEmpty && bodyType == EMPTY_OR_TEMPLATE) FROM_TEMPLATE else bodyType
    when (effectiveBodyType) {
        EMPTY_OR_TEMPLATE -> return ""
        FROM_TEMPLATE -> {
            val templateKind = if (descriptor is FunctionDescriptor) TemplateKind.FUNCTION else TemplateKind.PROPERTY_INITIALIZER
            return getFunctionBodyTextFromTemplate(
                project,
                templateKind,
                descriptor.name.asString(),
                descriptor.returnType?.let { IdeDescriptorRenderers.SOURCE_CODE.renderType(it) } ?: "Unit",
                null
            )
        }
        else -> return buildString {
            if (bodyType is Delegate) {
                append(bodyType.receiverName)
            } else {
                append("super")
                if (bodyType == QUALIFIED_SUPER) {
                    val superClassFqName = IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(
                        descriptor.containingDeclaration as ClassifierDescriptor
                    )
                    append("<").append(superClassFqName).append(">")
                }
            }
            append(".").append(descriptor.name.render())

            if (descriptor is FunctionDescriptor) {
                val paramTexts = descriptor.valueParameters.map {
                    val renderedName = it.name.render()
                    if (it.varargElementType != null) "*$renderedName" else renderedName
                }
                paramTexts.joinTo(this, prefix = "(", postfix = ")")
            }
        }
    }
}

fun KtNamedDeclaration.makeNotActual() {
    removeModifier(KtTokens.ACTUAL_KEYWORD)
    removeModifier(KtTokens.IMPL_KEYWORD)
}
