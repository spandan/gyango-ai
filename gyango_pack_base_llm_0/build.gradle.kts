plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("gyango_base_llm_0")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
