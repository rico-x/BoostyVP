package io.github.ricox.boostyvp.integration

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.ricox.boostyvp.MainActivity

data class ContentTarget(val blog: String, val post: String)
object ContentLinks {
    fun intent(context: Context, blog: String? = null, post: String? = null) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setData(if (blog != null && post != null)
            Uri.Builder().scheme("boostyvp").authority("post").appendPath(blog).appendPath(post).build()
            else Uri.parse("boostyvp://home"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    fun read(uri: Uri?): ContentTarget? {
        if (uri?.scheme != "boostyvp" || uri.host != "post" || uri.pathSegments.size != 2) return null
        val (blog, post) = uri.pathSegments
        if (blog.length !in 1..200 || post.length !in 1..200 || blog.any { it == '/' || it.isISOControl() } || post.any { it == '/' || it.isISOControl() }) return null
        return ContentTarget(blog, post)
    }
}
