package dev.miles.fliplink.data.shizuku

import android.os.Parcel
import android.os.Parcelable

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) : Parcelable {
    val isSuccess: Boolean
        get() = exitCode == 0

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(exitCode)
        dest.writeString(stdout)
        dest.writeString(stderr)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<ShellResult> = object : Parcelable.Creator<ShellResult> {
            override fun createFromParcel(source: Parcel): ShellResult {
                return ShellResult(
                    exitCode = source.readInt(),
                    stdout = source.readString().orEmpty(),
                    stderr = source.readString().orEmpty(),
                )
            }

            override fun newArray(size: Int): Array<ShellResult?> = arrayOfNulls(size)
        }
    }
}
