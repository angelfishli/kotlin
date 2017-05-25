/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.codegen

import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.TransientReceiver
import org.jetbrains.org.objectweb.asm.Type

object DestructuringDeclarationCodegen {
    fun genDestructuringDeclaration(codegen: ExpressionCodegen, destructuringDeclaration: KtDestructuringDeclaration, asProperty: Boolean) {
        val initializer = destructuringDeclaration.initializer ?: return
        val initializerType = codegen.bindingContext.getType(initializer) ?:
                              throw IllegalStateException("Type for initializer is null: $initializer")

        with(codegen) {
            val initializerAsmType = asmType(initializerType)

            gen(initializer, initializerAsmType)

            val tempVarIndex = myFrameMap.enterTemp(initializerAsmType)
            v.store(tempVarIndex, initializerAsmType)

            val local = StackValue.local(tempVarIndex, initializerAsmType)

            initializeDestructuringDeclarationVariables(
                    codegen, destructuringDeclaration, TransientReceiver(initializerType), local, asProperty)

            if (initializerAsmType.sort == Type.OBJECT || initializerAsmType.sort == Type.ARRAY) {
                v.aconst(null)
                v.store(tempVarIndex, initializerAsmType)
            }

            myFrameMap.leaveTemp(initializerAsmType)
        }
    }

    fun initializeDestructuringDeclarationVariables(
            codegen: ExpressionCodegen,
            destructuringDeclaration: KtDestructuringDeclaration,
            receiver: ReceiverValue,
            receiverStackValue: StackValue,
            asProperty: Boolean = false
    ) {
        for (variableDeclaration in destructuringDeclaration.entries) {
            val variableDescriptor = codegen.getVariableDescriptorNotNull(variableDeclaration)

            // Do not call `componentX` for destructuring entry called _
            if (variableDescriptor.name.isSpecial) continue

            val resolvedCall = codegen.bindingContext.get(BindingContext.COMPONENT_RESOLVED_CALL, variableDeclaration) ?:
                               error("Resolved call is null for " + variableDeclaration.text)
            val call = codegen.makeFakeCall(receiver)

            if (asProperty) {
                val propValue = codegen.intermediateValueForProperty(
                        variableDescriptor as PropertyDescriptor,
                        true,
                        false, null,
                        true,
                        StackValue.LOCAL_0, null)

                propValue.store(codegen.invokeFunction(call, resolvedCall, receiverStackValue), codegen.v)
            }
            else {
                codegen.initializeLocalVariable(variableDeclaration, codegen.invokeFunction(call, resolvedCall, receiverStackValue))
            }
        }
    }
}