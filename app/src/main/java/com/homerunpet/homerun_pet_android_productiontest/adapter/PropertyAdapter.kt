package com.homerunpet.homerun_pet_android_productiontest.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.homerunpet.homerun_pet_android_productiontest.data.Property1
import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemBoolBinding
import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemEnumBinding
import com.homerunpet.homerun_pet_android_productiontest.databinding.ItemNumberBinding
import com.homerunpet.homerun_pet_android_productiontest.ui.popup.ModelValueEditorPopup

import com.lxj.xpopup.XPopup
import java.util.Locale

class PropertyAdapter (
    private val propertyData:MutableList<Property1> = mutableListOf(),
    private val listener: PropertyItemActionListener
):RecyclerView.Adapter<RecyclerView.ViewHolder>(){
    private val TAG="PropertyAdapter"
    private val isRW=false

    companion object{
        private const val TYPE_ENUM=0
        private const val TYPE_NUMBER = 1
        private const val TYPE_BOOL = 2
    }

    fun submitList(list: List<Property1>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {

            override fun getOldListSize(): Int = propertyData.size

            override fun getNewListSize(): Int = list.size

            //判断是否是同一个item，其实是查看id判断的，需要重写ModelData属性id
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return propertyData[oldItemPosition] == list[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return propertyData[oldItemPosition] == list[newItemPosition]
            }
        })

        propertyData.clear()
        propertyData.addAll(list)  //propertyData有数据了

        diffResult.dispatchUpdatesTo(this)
    }

    // 根据接口返回的每个 item 的 type 决定布局类型
    override fun getItemViewType(position: Int): Int {
        return when (typeToInt(propertyData[position])) {
            ValueType.ENUM -> TYPE_ENUM
            ValueType.NUMBER -> TYPE_NUMBER
            ValueType.BOOL -> TYPE_BOOL
        }
    }
    private enum class ValueType{ENUM, NUMBER, BOOL }

    private fun typeToInt(item:Property1):ValueType{
        return when(item.type){
            "enum"->ValueType.ENUM
            "number"->ValueType.NUMBER
            "bool"->ValueType.BOOL
            //其他type String,Array这种类型都弄成number吧，因为目前不知道这两类型的ui
            else->ValueType.NUMBER
        }
    }


    private fun View.setReadWriteMode(canEdit: Boolean) {
        isClickable = canEdit
        isEnabled = canEdit
        alpha = if (canEdit) 1f else 0.5f
    }

    inner class EnumViewHolder(
        private  val binding: ItemEnumBinding): RecyclerView.ViewHolder(binding.root){

        fun bind(item: Property1){
            val (name, enName, typeText, rwText, enumOptions) = extractCommon(item)
            binding.tvName.text = name
            binding.tvEnName.text = enName
            binding.tvType.text = typeText
            binding.tvRwTag.text = rwText
            binding.etValue.setText(item.current_value?.toString() ?: "N/A", false)

            //这个enumOptions就是为了提取的spec.enum中有的枚举数据，
            val enumItems = enumOptions?.ifEmpty { listOf("N/A") }?:return

            // 失去焦点、只能聚焦于触摸
            binding.etValue.isFocusable = false
            binding.etValue.isFocusableInTouchMode = false

            val canEdit = item.access_mode == "rw"
            binding.etValue.setReadWriteMode(canEdit)

            if (canEdit) {
                binding.etValue.setOnClickListener {
                        val pos = bindingAdapterPosition
                        if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                        val currentItem = propertyData[pos]

                        // enum 用 index 映射成 List<Int>
                        val values = enumItems.indices.toList()
                        val currentIndex = enumItems.indexOf(binding.etValue.text?.toString().orEmpty()).takeIf { it >= 0 }

                        XPopup.Builder(binding.root.context)
                            .dismissOnTouchOutside(true)
                            .asCustom(
                                ModelValueEditorPopup(
                                    context = binding.root.context,
                                    title = item.name,
                                    values = values,
                                    initialValue = currentIndex,
                                    valueFormatter = { idx -> enumItems.getOrElse(idx) { "N/A" } },//idx是索引，item是List<String>数组
                                    onConfirm = { idx ->
                                        val selected = enumItems.getOrElse(idx) { "N/A" }
                                        binding.etValue.setText(selected, false)
                                        listener.onEnumItemSelected(currentItem, selected)
                                    },
                                )
                            )
                            .show()

                }
            } else {
                binding.etValue.setOnClickListener(null)
            }

        }
    }
    inner class NumberViewHolder(
        private val binding: ItemNumberBinding): RecyclerView.ViewHolder(binding.root){


        fun bind(item: Property1){
            val (name, enName, typeText, rwText, _) = extractCommon(item)
            binding.tvName.text = item.name
            binding.tvEnName.text = enName
            binding.tvType.text = typeText
            binding.tvRwTag.text = rwText
            Log.d(TAG, "bind: NumberViewHolder中${item.current_value}")
            binding.etValue.setText(item.current_value?.toString()?.toDoubleOrNull()?.toInt()?.toString() ?: "N/A")

            val specs =item.specs
            binding.etValue.isFocusable = false
            binding.etValue.isFocusableInTouchMode = false


            val canEdit = item.access_mode == "rw"
            binding.etValue.setReadWriteMode(canEdit)

            if (canEdit) {
                binding.etValue.setOnClickListener {
                        val pos = bindingAdapterPosition
                        if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                        val currentItem = propertyData[pos]

                        val min = specs?.min ?: 0
                        val max = specs?.max ?: min
                        val step = (specs?.step ?: 1).coerceAtLeast(1)
                        val unit = specs?.unit.orEmpty()
                        Log.d(TAG, "min=$min max=$max step=$step values=${buildSteppedValues(min, max, step)}")

                        val values = buildSteppedValues(min, max, step)
                        Log.d(TAG, "NumberViewHolder的bind中将要打开弹窗前，准备的值$values: ")


                        val initial = binding.etValue.text?.toString()?.trim()?.toIntOrNull()

                        XPopup.Builder(binding.root.context)
                            .dismissOnTouchOutside(true)
                            .asCustom(
                                ModelValueEditorPopup(
                                    context = binding.root.context,
                                    title = name,
                                    values = values,
                                    initialValue = initial,
                                    valueFormatter = { v -> formatNumberValue(enName, unit, min, max, step, v) },
                                    onConfirm = { v ->
                                        binding.etValue.setText(v.toString())
                                        listener.onNumberValueSelected(currentItem, v)
                                    },
                                )
                            )
                            .show()

                }
            } else {
                binding.etValue.setOnClickListener(null)
            }


        }

    }
    inner class BoolViewHolder(
        private val binding: ItemBoolBinding): RecyclerView.ViewHolder(binding.root){

        fun bind(item: Property1){
            val (name, key, typeText, rwText, _) = extractCommon(item)
            binding.tvName.text = name
            binding.tvKey.text = key
            binding.tvType.text = typeText
            binding.tvRwTag.text = rwText

            val canEdit = item.access_mode == "rw"
            binding.switchValue.setReadWriteMode(canEdit)

            if (canEdit) {
                binding.switchValue.setOnCheckedChangeListener(null)   // 先清掉监听
                binding.switchValue.isChecked = item.current_value == true // 设置当前值
                binding.switchValue.setOnCheckedChangeListener { _, isChecked ->
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener
                    listener.onBoolValueChanged(propertyData[pos], isChecked)
                }
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when(viewType){
            TYPE_ENUM->{
                val binding=ItemEnumBinding.inflate(LayoutInflater.from(parent.context),parent,false)
                EnumViewHolder(binding)
            }
            TYPE_NUMBER->{
                val binding=ItemNumberBinding.inflate(LayoutInflater.from(parent.context),parent,false)
                NumberViewHolder(binding)
            }
            TYPE_BOOL->{
                val binding=ItemBoolBinding.inflate(LayoutInflater.from(parent.context),parent,false)
                BoolViewHolder(binding)
            }else-> throw IllegalArgumentException("Invalid view type: $viewType")

            }
    }

    override fun getItemCount(): Int=propertyData.size


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(holder){
            is EnumViewHolder->holder.bind(propertyData[position])
            is NumberViewHolder->holder.bind(propertyData[position])
            is BoolViewHolder->holder.bind(propertyData[position])
        }
    }

    private data class CommonUi(
        val name: String,
        val enName: String,
        val typeText: String,
        val rwText: String,
        val enumOptions: List<String>?=null,
        val value:Any?
    )

    private fun extractCommon(item: Property1): CommonUi {
        return when (item.type) {
            "enum" -> CommonUi(
                name = item.name,
                enName = item.identifier,
                typeText = item.type.uppercase(),
                rwText = item.access_mode.uppercase(),
                enumOptions = item.specs?.enum ?: emptyList(),
                value = item.current_value
            )
            "number" -> CommonUi(
                name = item.name,
                enName = item.identifier,
                typeText = item.type.uppercase(),
                rwText = item.access_mode.uppercase(),
                value = item.current_value

            )
            "bool" -> CommonUi(
                name = item.name ,
                enName = item.identifier ,
                typeText = item.type .uppercase(),
                rwText =  item.access_mode.uppercase(),
                value = item.current_value
            )
            //item.type的还有String、array，不知道页面暂时不写新的viewHolder
            else -> CommonUi(
                name = item.name,
                enName = item.identifier,
                typeText = item.type.uppercase(),
                rwText = item.access_mode.uppercase(),
                value = item.current_value
            )
        }

    }



    //NumberPicker，，，有max、min、step的数据
    private fun buildSteppedValues(min: Int, max: Int, step: Int): List<Int> {
        if (step <= 0) return listOf(min)
        if (max <= min) return listOf(min)

        //初始化数组容量大小
        val list = ArrayList<Int>(((max - min) / step) + 1)

        //开始向数组每个步位对应的数据
        var v = min
        while (v <= max) {
            list.add(v)
            v += step
        }
        //对于最后一位，如果v+step>max，那就填写max就行了
        if (list.lastOrNull() != max && (max - min) % step != 0) {
            // 末尾补齐 max，避免因为步长无法整除导致 max 不可选
            list.add(max)
        }
        return list
    }

    private fun formatNumberValue(
        identifier: String,
        unit: String,
        min: Int,
        max: Int,
        step: Int,
        value: Int,
    ): String {
        val id = identifier.lowercase(Locale.getDefault())
        val u = unit.lowercase(Locale.getDefault())

        // 1) 倒计时/时间类：unit=min 且 step>=60 时，按“X小时Y分钟”展示
        if (u == "min" && step >= 60) {
            val totalMin = value.coerceIn(min, max)
            val h = totalMin / 60
            val m = totalMin % 60
            return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
        }

        // 2) 档位类：0=关闭，其余显示“X档”
        if (min == 0 && max in 3..20 && step == 1 && (id.contains("fan") || id.contains("speed") || id.contains("gear"))) {
            return if (value == 0) "关闭" else "${value}档"
        }

        // 3) 通用数值：带单位
        return if (unit.isBlank()) value.toString() else "$value $unit"
    }
}