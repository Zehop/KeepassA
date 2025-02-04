/*
 * Copyright (C) 2020 AriaLyy(https://github.com/AriaLyy/KeepassA)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/.
 */


package com.lyy.keepassa.view.dialog

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.FrameLayout
import android.widget.TimePicker
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.arialyy.frame.util.DpUtils
import com.google.android.material.tabs.TabLayoutMediator
import com.lyy.keepassa.R
import com.lyy.keepassa.base.BaseDialog
import com.lyy.keepassa.databinding.DialogTimerBinding
import com.lyy.keepassa.event.TimeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

/**
 * 时间选择器
 */
@Route(path = "/dialog/timeChange")
class TimeChangeDialog : BaseDialog<DialogTimerBinding>(), View.OnClickListener {
  private lateinit var vpAdapter: VpAdapter
  private val fragments = arrayListOf<Fragment>()

  var timeFlow =  MutableStateFlow<TimeEvent?>(null)

  override fun setLayoutId(): Int {
    return R.layout.dialog_timer
  }

  override fun initData() {
    super.initData()
    val titles = listOf(getString(R.string.date), getString(R.string.time))
    fragments.add(DatePickerFragment())
    fragments.add(TimerPickerFragment())
    vpAdapter = VpAdapter(fragments, this)
    binding.vp.adapter = vpAdapter
    binding.vp.offscreenPageLimit = 2
    TabLayoutMediator(binding.tabLayout, binding.vp) { tab, position ->
      tab.text = titles[position]
    }.attach()
    binding.cancel.setOnClickListener(this)
    binding.save.setOnClickListener(this)

  }

  override fun onClick(v: View?) {
    when (v!!.id) {
      R.id.cancel -> dismiss()
      R.id.save -> {
        val date = (fragments[0] as DatePickerFragment).datePicker
        val time = (fragments[1] as TimerPickerFragment).timerPicker
        val event: TimeEvent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
          TimeEvent(
            date.year,
            date.month + 1,
            date.dayOfMonth,
            time.currentHour,
            time.currentMinute
          )
        } else {
          TimeEvent(date.year, date.month + 1, date.dayOfMonth, time.hour, time.minute)
        }
        lifecycleScope.launch {
          timeFlow.emit(event)
        }
        dismiss()
      }
    }
  }

  override fun onStart() {
    super.onStart()
    dialog?.window?.setLayout(DpUtils.dp2px(280), DpUtils.dp2px(530))
  }

  private class VpAdapter(
    private val fragments: List<Fragment>,
    fm: Fragment
  ) : FragmentStateAdapter(fm) {

    override fun getItemCount(): Int {
      return fragments.size
    }

    override fun createFragment(position: Int): Fragment {
      return fragments[position]
    }
  }

  class DatePickerFragment : Fragment() {
    val datePicker by lazy {
      DatePicker(requireContext())
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
      datePicker.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
      )
      return datePicker
    }
  }

  class TimerPickerFragment : Fragment() {
    val timerPicker by lazy {
      TimePicker(requireContext())
    }

    override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
    ): View {
      timerPicker.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
      )

      return timerPicker
    }

  }

}