package net.kztmc.mc.autonomousbot.crafting;

import java.util.Map;

/**
 * 3x3クラフト台グリッドの静的レシピ定義。
 * gridSlots のキーは 1〜9 のセル番号 (TL=1,TM=2,TR=3,ML=4,MM=5,MR=6,BL=7,BM=8,BR=9)、
 * 値はそのセルへ入れる材料の完全なitemId。
 */
public record Recipe(String id, String resultItemId, Map<Integer, String> gridSlots) {
}