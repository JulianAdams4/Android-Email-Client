package com.email.scenes.labelChooser

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.email.R
import com.email.scenes.labelChooser.data.LabelWrapper
import com.email.scenes.labelChooser.holders.LabelHolder
import com.email.utils.VirtualList

/**
 * Created by sebas on 2/2/18.
 */

class LabelWrapperAdapter(private val mContext : Context,
                          var labelWrapperListener : OnLabelWrapperEventListener?,
                          private val labelList: VirtualList<LabelWrapper>)
    : RecyclerView.Adapter<LabelHolder>() {

    private fun onToggleLabelSelection(labelWrapper: LabelWrapper, position: Int) {
        labelWrapperListener?.onToggleLabelSelection(labelWrapper, position)
    }

    override fun onBindViewHolder(holder: LabelHolder?, position: Int) {
        if(holder?.itemView == null) return
        val labelThread = labelList[position]
        holder.bindLabel(labelThread)

        val itemClickListener = {
            onToggleLabelSelection(labelThread, position)
        }
        holder.setOnCheckboxClickedListener(itemClickListener)

        holder.itemView.setOnClickListener({
            onToggleLabelSelection(labelThread, position)
        })
    }

    override fun getItemCount(): Int {
        return labelList.size
    }

    private fun createMailItemView(): View {
        return View.inflate(mContext, R.layout.label_item, null )
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelHolder {
        val itemView : View = createMailItemView()
        return LabelHolder(itemView)
    }

    interface OnLabelWrapperEventListener{
        fun onToggleLabelSelection(label: LabelWrapper, position: Int)
    }
}