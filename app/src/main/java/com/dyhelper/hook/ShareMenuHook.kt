package com.dyhelper.hook

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dyhelper.util.HookUtils
import de.robv.android.xposed.XC_MethodHook

class ShareMenuHook(private val copyFn:(Context)->Unit,private val videoFn:(Context)->Unit,private val audioFn:(Context)->Unit,private val imageFn:(Context)->Unit,private val checkImage:()->Boolean):BaseHook{
    companion object{private const val TAG_BTN=888888}

    data class MI(val label:String,val action:Runnable)
    class MA(items:List<MI>):RecyclerView.Adapter<MA.VH>(){
        private val d=items.toList()
        class VH(view:TextView):RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(p:ViewGroup,vt:Int):VH{val tv=TextView(p.context);tv.textSize=12f;tv.setTextColor(Color.parseColor("#CCCCCC"));tv.gravity=Gravity.CENTER;tv.setPadding(20,12,20,12);return VH(tv)}
        override fun onBindViewHolder(vh:VH,pos:Int){(vh.itemView as TextView).text=d[pos].label;vh.itemView.setOnClickListener{d[pos].action.run()}}
        override fun getItemCount()=d.size
    }

    override fun name()="Menu"

    private fun injectMenu(parent:ViewGroup,ctx:Context){
        if(parent.findViewWithTag<View>(TAG_BTN)!=null)return
        val img=checkImage()
        val items=listOf(
            MI("\u590D\u5236\u94FE\u63A5",Runnable{copyFn(ctx)}),
            MI(if(img)"\u56FE\u7247\u4E0B\u8F7D" else "\u89C6\u9891\u4E0B\u8F7D",Runnable{if(img)imageFn(ctx)else videoFn(ctx)}),
            MI("\u97F3\u9891\u4E0B\u8F7D",Runnable{audioFn(ctx)})
        )
        val rv=RecyclerView(ctx).apply{layoutManager=LinearLayoutManager(ctx,LinearLayoutManager.HORIZONTAL,false);adapter=MA(items);tag=TAG_BTN;setPadding(13,0,13,0)}
        parent.addView(rv,0)
        HookUtils.log("[Menu] Injected into "+parent.javaClass.simpleName)
    }

    private fun findAndInject(root:View):Boolean{
        if(root is ViewGroup && root.javaClass.name.contains("ActionBar")){injectMenu(root,root.context);return true}
        if(root is ViewGroup){for(i in 0 until root.childCount){if(findAndInject(root.getChildAt(i)))return true}}
        return false
    }

    private fun isSharePanel(view:ViewGroup):Boolean{
        if(view.childCount<2)return false
        val cn=view.javaClass.name.lowercase()
        if(cn.contains("share")&&(cn.contains("panel")||cn.contains("dialog")||cn.contains("sheet")||cn.contains("board"))||cn.contains("sharesheet")||cn.contains("shareboard"))return true
        if(cn.contains("social")&&cn.contains("action"))return true
        for(i in 0 until minOf(view.childCount,8)){
            val child=view.getChildAt(i)
            if(child is TextView){
                val t=child.text?.toString()?:"";if(t.contains("\u5206\u4EAB")||t.contains("\u8F6C\u53D1")||t.contains("\u53D1\u9001\u7ED9")||t.contains("\u590D\u5236\u94FE\u63A5")||t.contains("\u4FDD\u5B58"))return true
            }
        }
        return false
    }

    private fun checkAndInject(container:ViewGroup){
        if(container.findViewWithTag<View>(TAG_BTN)!=null)return
        try{
            if(isSharePanel(container)){
                HookUtils.log("[Menu] SharePanel detected: "+container.javaClass.name)
                container.post{injectMenu(container,container.context)}
            }
        }catch(_:Throwable){}
    }

    override fun init(loader:ClassLoader):Boolean{
        var ok=false

        // Approach 1: View.onAttachedToWindow (catches all views including non-Dialog share panels)
        try{
            val viewClass=Class.forName("android.view.View",false,loader)
            if(HookUtils.hookAllMethods(viewClass,"onAttachedToWindow",object:XC_MethodHook(){
                override fun afterHookedMethod(p:MethodHookParam){
                    try{val v=p.thisObject as? ViewGroup?:return;v.post{checkAndInject(v)}}catch(_:Throwable){}
                }
            })){
                HookUtils.log("[Menu] View.onAttachedToWindow hook OK");ok=true
            }
        }catch(t:Throwable){HookUtils.log("[Menu] View hook err: "+t.message)}

        // Approach 2: Dialog.show (fallback for dialog-based share panels)
        try{
            val dlgClass=Class.forName("android.app.Dialog",false,loader)
            if(HookUtils.hookAllMethods(dlgClass,"show",object:XC_MethodHook(){
                override fun afterHookedMethod(p:MethodHookParam){
                    val cn=p.thisObject.javaClass.name.lowercase()
                    if(!cn.contains("social")&&!cn.contains("share")&&!cn.contains("panel"))return
                    HookUtils.log("[Menu] Dialog.show: "+cn)
                    Handler(Looper.getMainLooper()).postDelayed({
                        try{val dlg=p.thisObject as Dialog;val dec=dlg.window?.decorView?:return@postDelayed;findAndInject(dec)}catch(t:Throwable){HookUtils.log("[Menu] Dlg err: "+t.message)}
                    },500)
                }
            })){
                HookUtils.log("[Menu] Dialog.show hook OK");ok=true
            }
        }catch(t:Throwable){HookUtils.log("[Menu] Dialog hook err: "+t.message)}

        return ok
    }
}
