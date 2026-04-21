plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("gyango_base_llm_1")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
