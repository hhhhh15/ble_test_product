package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product

import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemBledeviceBinding

class DeviceListAdapter (
    private val onItemClick: (Product) -> Unit
) : RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<Product>()


    //ViewHolder类
    inner class DeviceViewHolder(val binding: ItemBledeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mixProduct: Product) {
            binding.tvProductName.text="产品名:" +mixProduct.name
            binding.tvDeviceKey.text = "设备pk："+ mixProduct.hmFastBleDevice?.pk
            binding.tvDeviceMac.text="mac:" + mixProduct.hmFastBleDevice?.mac
//            binding.tvDeviceDeviceSerial.text="序列号:"+mixProduct.deviceSerial
            binding.btnConnect.setOnClickListener {
                Log.d("hhhAdapter", "🔥 按钮被点击了！设备: ${mixProduct.name}")
                onItemClick(mixProduct)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemBledeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        Log.d("Adapter", "✅ ViewHolder 创建了")
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)

    }

    override fun getItemCount(): Int  = devices.size

    fun submitList(newList: List<Product>) {
        devices.clear()
        devices.addAll(newList)
        notifyDataSetChanged()
    }


}