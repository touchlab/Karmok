package co.touchlab.karmok

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

class MockNameMap:HashMap<OverrideMemberChooserObject, String>(){
    fun getName(key:OverrideMemberChooserObject)= makeMockerName(key.descriptor)
    private fun makeMockerName(descriptor: CallableMemberDescriptor): String {
        return "${descriptor.name}"
    }
}