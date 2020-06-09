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
            val elementList = nameMap.get(baseName)!!
            if (elementList.size > 1) {
                val multiple = elementList.filter { it.descriptor is FunctionDescriptor }.size > 1
                val theName = if(multiple){
                    var nameCount = 0
                    var maybeName = ""
                    do {
                        maybeName = "${baseName}_fun${nameCount++}"
                    }while (values.contains(maybeName) || findPropWithName(mockClass, maybeName) != null)
                    maybeName
                }else{
                    val sb = StringBuilder("${baseName}_fun")
                    while (values.contains(sb.toString()) || findPropWithName(mockClass, sb.toString()) != null) {
                        sb.append("_")
                    }
                    sb.toString()
                }

                put(key, theName)
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