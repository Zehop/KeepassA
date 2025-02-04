/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.fingerprint

import android.annotation.SuppressLint
import android.os.Build.VERSION_CODES
import android.view.View
import androidx.annotation.RequiresApi
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.AuthenticationCallback
import androidx.biometric.BiometricPrompt.AuthenticationResult
import androidx.biometric.BiometricPrompt.CryptoObject
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.lifecycle.ViewModelProvider
import com.arialyy.frame.util.KeyStoreUtil
import com.arialyy.frame.util.ResUtil
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseApp
import com.lyy.keepassa.base.BaseFragment
import com.lyy.keepassa.common.PassType
import com.lyy.keepassa.databinding.FragmentFingerprintDesxBinding
import com.lyy.keepassa.entity.QuickUnLockRecord
import com.lyy.keepassa.util.HitUtil
import com.lyy.keepassa.util.VibratorUtil
import timber.log.Timber
import java.security.KeyStoreException

/**
 * 指纹解锁描述
 */
@RequiresApi(VERSION_CODES.M)
class FingerprintDescFragment : BaseFragment<FragmentFingerprintDesxBinding>(),
  View.OnClickListener {

  private val keyStoreUtil = KeyStoreUtil()
  private var isFullUnlock: Boolean = false
  private lateinit var module: FingerprintModule
  private var lastFlag = FingerprintActivity.FLAG_CLOSE

  override fun initData() {
    module = ViewModelProvider(requireActivity()).get(FingerprintModule::class.java)
    if (module.curFlag == FingerprintActivity.FLAG_FULL_UNLOCK) {
      isFullCheck(true)
    } else if (module.curFlag == FingerprintActivity.FLAG_QUICK_UNLOCK) {
      isFullCheck(false)
    }
    lastFlag = module.curFlag

    binding.rlQuick.setOnClickListener(this)
    binding.rlFull.setOnClickListener(this)
  }

  override fun setLayoutId(): Int {
    return R.layout.fragment_fingerprint_desx
  }

  override fun onClick(v: View) {
    if (v.id == R.id.rlQuick) {
      isFullUnlock = false
      module.curFlag = FingerprintActivity.FLAG_QUICK_UNLOCK
      showBiometricPrompt()
      isFullCheck(isFullUnlock)
      return
    }

    if (v.id == R.id.rlFull) {
      isFullUnlock = true
      module.curFlag = FingerprintActivity.FLAG_FULL_UNLOCK
      showBiometricPrompt()
      isFullCheck(isFullUnlock)
    }
  }

  private fun isFullCheck(isFull: Boolean) {
    binding.scFull.isChecked = isFull
    binding.scQuick.isChecked = !isFull
  }

  /**
   * 恢复状态
   */
  private fun goBackCheckStat() {
    when (lastFlag) {
      FingerprintActivity.FLAG_QUICK_UNLOCK -> isFullCheck(false)
      FingerprintActivity.FLAG_FULL_UNLOCK -> isFullCheck(true)
      else -> {
        binding.scFull.isChecked = false
        binding.scQuick.isChecked = false
      }
    }
  }

  fun showBiometricPrompt() {
    if (BaseApp.passType == PassType.ONLY_KEY) {
      showOnlyKeyBiometricPrompt()
      return
    }

    showNormalBiometricPrompt()
  }

  /**
   * 处理数据库有密码的指纹解锁配置
   * https://developer.android.com/training/sign-in/biometric-auth#kotlin
   */
  @SuppressLint("RestrictedApi")
  fun showNormalBiometricPrompt() {
    val promptInfo = PromptInfo.Builder()
      .setTitle(ResUtil.getString(R.string.fingerprint_unlock))
      .setSubtitle(ResUtil.getString(R.string.verify_finger))
      .setNegativeButtonText(ResUtil.getString(R.string.cancel))

      // DEVICE_CREDENTIAL pin 码
      // BIOMETRIC_STRONG 指纹
      .setAllowedAuthenticators(BIOMETRIC_STRONG)
//        .setConfirmationRequired(false)
      .build()
    val biometricPrompt = BiometricPrompt(this, ArchTaskExecutor.getMainThreadExecutor(),
      object : AuthenticationCallback() {
        override fun onAuthenticationError(
          errorCode: Int,
          errString: CharSequence
        ) {
          goBackCheckStat()
          if (!isAdded) {
            return
          }
          val str = if (errorCode == ERROR_NEGATIVE_BUTTON) {
            "${ResUtil.getString(R.string.verify_finger)}${ResUtil.getString(R.string.cancel)}"
          } else {
            ResUtil.getString(R.string.verify_finger_fail)
          }
          HitUtil.snackShort(mRootView, str)
        }

        override fun onAuthenticationSucceeded(result: AuthenticationResult) {
          super.onAuthenticationSucceeded(result)

          try {
            val auth: CryptoObject? = result.cryptoObject
            if (auth == null || auth.cipher == null) {
              return
            }
            val cipher = auth.cipher!!
            val useKey = BaseApp.dbKeyPath.isNullOrEmpty()

            Timber.d(
              "passLen = ${BaseApp.dbPass.length}, cipherBloack = ${cipher.blockSize}, byteLen = ${
                BaseApp.dbPass.toByteArray(
                  Charsets.UTF_8
                ).size
              }"
            )
            // val encrypted = cipher.doFinal(BaseApp.dbPass.toByteArray(Charsets.UTF_8))

            val passPair = keyStoreUtil.encryptData(cipher, BaseApp.dbPass)
            val quickInfo = QuickUnLockRecord(
              dbUri = BaseApp.dbRecord!!.localDbUri,
              dbPass = passPair.first,
              keyPath = BaseApp.dbKeyPath,
              isUseKey = useKey,
              isUseFingerprint = isFullUnlock,
              passIv = passPair.second
            )
            module.saveNormalQuickInfo(quickInfo)
            HitUtil.toaskShort("${ResUtil.getString(R.string.verify_finger)} ${ResUtil.getString(R.string.success)}")
            VibratorUtil.vibrator(300)

            module.oldFlag = if (isFullUnlock) {
              FingerprintActivity.FLAG_FULL_UNLOCK
            } else {
              FingerprintActivity.FLAG_QUICK_UNLOCK
            }

            requireActivity().finishAfterTransition()
            lastFlag = module.curFlag
          } catch (e: KeyStoreException) {
            Timber.e(e)
            keyStoreUtil.deleteKeyStore()
            keyStoreUtil.load(null)
            HitUtil.snackShort(mRootView, ResUtil.getString(R.string.error_keystore))
          } catch (e: Exception) {
            Timber.e(e)
            keyStoreUtil.deleteKeyStore()
            HitUtil.snackLong(mRootView, ResUtil.getString(R.string.error_keystore))
          }
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          goBackCheckStat()
          HitUtil.snackShort(mRootView, ResUtil.getString(R.string.verify_finger_fail))
        }
      })
    try {
      // Displays the "log in" prompt.
      biometricPrompt.authenticate(
        promptInfo,
        CryptoObject(keyStoreUtil.getEncryptCipher())
      )
    } catch (e: Exception) {
      keyStoreUtil.deleteKeyStore()
      Timber.e(e)
    }
  }

  /**
   * 处理数据库仅使用密钥的指纹解锁配置
   * https://developer.android.com/training/sign-in/biometric-auth#kotlin
   */
  @SuppressLint("RestrictedApi")
  fun showOnlyKeyBiometricPrompt() {
    val promptInfo = PromptInfo.Builder()
      .setTitle(ResUtil.getString(R.string.fingerprint_unlock))
      .setSubtitle(ResUtil.getString(R.string.verify_finger))
      .setNegativeButtonText(ResUtil.getString(R.string.cancel))
//        .setConfirmationRequired(false)
      .build()
    val biometricPrompt = BiometricPrompt(this, ArchTaskExecutor.getMainThreadExecutor(),
      object : AuthenticationCallback() {
        override fun onAuthenticationError(
          errorCode: Int,
          errString: CharSequence
        ) {
          goBackCheckStat()
          if (!isAdded) {
            return
          }
          val str = if (errorCode == ERROR_NEGATIVE_BUTTON) {
            "${ResUtil.getString(R.string.verify_finger)}${ResUtil.getString(R.string.cancel)}"
          } else {
            ResUtil.getString(R.string.verify_finger_fail)
          }
          HitUtil.snackShort(mRootView, str)
        }

        override fun onAuthenticationSucceeded(result: AuthenticationResult) {
          super.onAuthenticationSucceeded(result)

          val useKey = BaseApp.dbKeyPath.isNotEmpty()
          val quickInfo = QuickUnLockRecord(
            dbUri = BaseApp.dbRecord!!.localDbUri,
            keyPath = BaseApp.dbKeyPath,
            isUseKey = useKey,
            isUseFingerprint = isFullUnlock,
          )
          module.saveOnlyKeyQuickInfo(quickInfo)

          HitUtil.toaskShort("${ResUtil.getString(R.string.verify_finger)} ${ResUtil.getString(R.string.success)}")
          VibratorUtil.vibrator(300)

          module.oldFlag = if (isFullUnlock) {
            FingerprintActivity.FLAG_FULL_UNLOCK
          } else {
            FingerprintActivity.FLAG_QUICK_UNLOCK
          }

          requireActivity().finishAfterTransition()
          lastFlag = module.curFlag
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          goBackCheckStat()
          HitUtil.snackShort(mRootView, ResUtil.getString(R.string.verify_finger_fail))
        }
      })
    biometricPrompt.authenticate(promptInfo)
  }
}