package permissions.dispatcher.processor.impl

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.MethodSpec
import permissions.dispatcher.processor.RuntimePermissionsElement
import permissions.dispatcher.processor.util.*
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

/**
 * ProcessorUnit implementation for Activity classes
 */
class ActivityProcessorUnit : BaseProcessorUnit() {

    private val PERMISSION_UTILS: ClassName = ClassName.get("permissions.dispatcher", "PermissionUtils")
    private val ACTIVITY_COMPAT: ClassName = ClassName.get("android.support.v4.app", "ActivityCompat")

    override fun getTargetType(): TypeMirror {
        return typeMirrorOf("android.app.Activity")
    }

    override fun checkPrerequisites(rpe: RuntimePermissionsElement) {
        // Nothing to check
    }

    override fun addWithCheckBody(builder: MethodSpec.Builder, needsMethod: ExecutableElement, rpe: RuntimePermissionsElement, targetParam: String) {
        // Create field names for the constants to use
        val requestCodeField = requestCodeFieldName(needsMethod)
        val permissionField = permissionFieldName(needsMethod)

        // Add the conditional for when permission has already been granted
        builder.beginControlFlow("if (\$T.hasSelfPermissions(\$N, \$N))", PERMISSION_UTILS, targetParam, permissionField)
        builder.addStatement("\$N.\$N()", targetParam, needsMethod.simpleString())

        // Add the conditional for "OnShowRationale", if present
        val onRationale: ExecutableElement? = rpe.findOnRationaleForNeeds(needsMethod)
        if (onRationale != null) {
            builder.nextControlFlow("else if (\$T.shouldShowRequestPermissionRationale(\$N, \$N))", PERMISSION_UTILS, targetParam, permissionField)
            builder.addStatement("\$N.\$N(new \$N(\$N))", targetParam, onRationale.simpleString(), permissionRequestMethodName(needsMethod), targetParam)
        }

        // Add the branch for "request permission"
        builder.nextControlFlow("else")
        addRequestPermissionsStatement(builder, targetParam, permissionField, requestCodeField)
        builder.endControlFlow()
    }

    override fun addRequestPermissionsStatement(builder: MethodSpec.Builder, targetParam: String, permissionField: String, requestCodeField: String) {
        builder.addStatement("\$T.requestPermissions(\$N, \$N, \$N)", ACTIVITY_COMPAT, targetParam, permissionField, requestCodeField)
    }

    override fun addResultCaseBody(builder: MethodSpec.Builder, needsMethod: ExecutableElement, rpe: RuntimePermissionsElement, targetParam: String, grantResultsParam: String) {
        // Add the conditional for "permission verified"
        builder.beginControlFlow("if (\$T.verifyPermissions(\$N))", PERMISSION_UTILS, grantResultsParam)
        builder.addStatement("target.\$N()", needsMethod.simpleString())

        // Add the conditional for "permission denied", if present
        val onDenied: ExecutableElement? = rpe.findOnDeniedForNeeds(needsMethod)
        if (onDenied != null) {
            builder.nextControlFlow("else")
            builder.addStatement("\$N.\$N()", targetParam, onDenied.simpleString())
        }
        // Close the control flow
        builder.endControlFlow()
        builder.addStatement("break");
    }
}