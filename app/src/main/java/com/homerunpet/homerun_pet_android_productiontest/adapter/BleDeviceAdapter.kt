package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.homerunpet.homerun_pet_android_productiontest.ble.model.HMFastBleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemBledeviceBinding

class BleDeviceAdapter(
    private val productMap: Map<String, Product> // 添加 productMap
) : ListAdapter<HMFastBleDevice, BleDeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    var onItemClick: ((Product) -> Unit)? = null // 改为传递 Product

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBledeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position), productMap)
    }

    inner class DeviceViewHolder(val binding: ItemBledeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: HMFastBleDevice, productMap: Map<String, Product>) {
            binding.tvDeviceKey.text = device.pk

            // 从 productMap 获取对应的 product
            val product = productMap[device.pk]
            binding.tvProductName.text = product?.name ?: "未知产品"

            binding.btnConnect.setOnClickListener {
                // 点击时传递 product 而不是 device
                product?.let {
                    onItemClick?.invoke(it)
                }
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<HMFastBleDevice>() {
        override fun areItemsTheSame(oldItem: HMFastBleDevice, newItem: HMFastBleDevice): Boolean {
            return oldItem.mac == newItem.mac
        }

        override fun areContentsTheSame(oldItem: HMFastBleDevice, newItem: HMFastBleDevice): Boolean {
            return oldItem == newItem
        }
    }
}