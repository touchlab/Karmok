package co.touchlab.karmok

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtProperty

class MockNameMap(
    val mockClass: KtClass,
    selectedElements: Collection<OverrideMemberChooserObject>
) : HashMap<OverrideMemberChooserObject, String>() {
    init {
        val nameMap = HashMap<String, MutableList<OverrideMemberChooserObject>>()
        selectedElements.forEach { key ->
            val baseName = makeMockerName(key.descriptor)

            val theList = nameMap.getOrPut(baseName) {
                mutableListOf()
            }

            theList.add(key)
        }

        //val elements with the same name as functions
        selectedElements.filter { it.descriptor !is FunctionDescriptor }.forEach { key ->
            val baseName = makeMockerName(key.descriptor)
            if (nameMap.get(baseName)!!.size > 1) {
                put(key, "${baseName}_val")
            }
        }

        //fun elements with the same name as other elements
        selectedElements.filter { it.descriptor is FunctionDescriptor }.forEach { key ->
            val baseName = makeMockerName(key.descriptor)
            if (nameMap.get(baseName)!!.size > 1) {
                val sb = StringBuilder("${baseName}_fun")
                while (values.contains(sb.toString()) || findPropWithName(mockClass, sb.toString()) != null) {
                    sb.append("_")
                }

                put(key, sb.toString())
            }
        }
    }

    fun getName(key: OverrideMemberChooserObject) = getOrPut(key) {
        val baseName = makeMockerName(key.descriptor)
        val sb = StringBuilder(baseName)
        while (findPropWithName(mockClass, sb.toString()) != null) {
            sb.append("_")
        }
        sb.toString()
    }

    fun findPropWithName(mockClass: KtClass, name: String): PsiElement? {
        return findElement(mockClass) {
            it is KtProperty && it.name.equals(name)
        }
    }

    private fun makeMockerName(descriptor: CallableMemberDescriptor): String {
        return "${descriptor.name}"
    }
}