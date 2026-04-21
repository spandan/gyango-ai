plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("gyango_base_llm_2")
    dynamicDelivery {
        deliveryType.set("on-demand")
    }
}
