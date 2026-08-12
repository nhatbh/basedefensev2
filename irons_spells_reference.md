# Iron's Spells & Spellbooks - Registered Spells Reference

This document indexes all **107** registered spells gathered from the Iron's Spells & Spellbooks mod cache (irons_spellbooks-1.20.1-3.15.4-sources.jar).
Each spell is classified by its **School**, **Cast Type**, **Mechanic Type**, and **onCast behavior** to guide the generic active skill rework.

---

## School: BLOOD

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **AcupunctureSpell** | irons_spellbooks:acupuncture | INSTANT | Raycast / Targeted | Applies spell damage |
| **BloodNeedlesSpell** | irons_spellbooks:bloodneedles | INSTANT | Raycast / Targeted | Applies spell damage |
| **BloodSlashSpell** | irons_spellbooks:bloodslash | INSTANT | Projectile | Instantiates BloodSlashProjectile, Applies spell damage, Teleports entity |
| **BloodStepSpell** | irons_spellbooks:bloodstep | INSTANT | Raycast / Targeted | Grants status effect, Teleports entity, Plays spell SFX |
| **DevourSpell** | irons_spellbooks:devour | INSTANT | Raycast / Targeted | Applies spell damage, Teleports entity |
| **HeartstopSpell** | irons_spellbooks:heartstop | INSTANT | Buff / Debuff | Grants status effect |
| **RaiseDeadSpell** | irons_spellbooks:raisedead | LONG | Movement / Teleport | Teleports entity, Plays spell SFX |
| **RayOfSiphoningSpell** | irons_spellbooks:rayofsiphoning | CONTINUOUS | Raycast / Targeted | Applies spell damage |
| **SacrificeSpell** | irons_spellbooks:sacrifice | INSTANT | Raycast / Targeted | Applies spell damage, Plays spell SFX |
| **WitherSkullSpell** | irons_spellbooks:witherskull | INSTANT | Projectile | Instantiates WitherSkullProjectile, Applies spell damage |

---

## School: ELDRITCH

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **AbyssalShroudSpell** | irons_spellbooks:abyssalshroud | INSTANT | Buff / Debuff | Grants status effect |
| **EldritchBlastSpell** | irons_spellbooks:eldritchblast | INSTANT | Raycast / Targeted | Instantiates EldritchBlastVisualEntity, Applies spell damage |
| **PlanarSightSpell** | irons_spellbooks:planarsight | INSTANT | Buff / Debuff | Grants status effect |
| **PocketDimensionSpell** | irons_spellbooks:pocketdimension | LONG | Movement / Teleport | Executes custom spell logic |
| **SculkTentaclesSpell** | irons_spellbooks:sculktentacles | LONG | Raycast / Targeted | Applies spell damage, Plays spell SFX |
| **SonicBoomSpell** | irons_spellbooks:sonicboom | LONG | Raycast / Targeted | Applies spell damage |
| **TelekinesisSpell** | irons_spellbooks:telekinesis | CONTINUOUS | Utility / Effect | Executes custom spell logic |

---

## School: ENDER

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **BlackHoleSpell** | irons_spellbooks:blackhole | LONG | Raycast / Targeted | Applies spell damage, Plays spell SFX |
| **CounterspellSpell** | irons_spellbooks:counterspell | INSTANT | Raycast / Targeted | Executes custom spell logic |
| **DragonBreathSpell** | irons_spellbooks:dragonbreath | CONTINUOUS | Projectile | Instantiates DragonBreathProjectile, Applies spell damage, Teleports entity |
| **EchoingStrikesSpell** | irons_spellbooks:echoingstrikes | INSTANT | Buff / Debuff | Grants status effect |
| **EvasionSpell** | irons_spellbooks:evasion | INSTANT | Buff / Debuff | Grants status effect |
| **MagicArrowSpell** | irons_spellbooks:magicarrow | LONG | Projectile | Instantiates MagicArrowProjectile, Applies spell damage, Teleports entity |
| **MagicMissileSpell** | irons_spellbooks:magicmissile | INSTANT | Projectile | Instantiates MagicMissileProjectile, Applies spell damage, Teleports entity |
| **PortalSpell** | irons_spellbooks:portal | INSTANT | Raycast / Targeted | Instantiates PortalEntity |
| **RecallSpell** | irons_spellbooks:recall | LONG | Movement / Teleport | Plays spell SFX |
| **ShadowSlashSpell** | irons_spellbooks:shadowslash | INSTANT | Raycast / Targeted | Applies spell damage, Grants status effect, Plays spell SFX |
| **StarfallSpell** | irons_spellbooks:starfall | CONTINUOUS | Raycast / Targeted | Executes custom spell logic |
| **SummonEnderChestSpell** | irons_spellbooks:summonenderchest | INSTANT | Utility / Effect | Executes custom spell logic |
| **SummonSwordsSpell** | irons_spellbooks:summonswords | LONG | Summoning | Instantiates SummonedClaymoreEntity |
| **TeleportSpell** | irons_spellbooks:teleport | INSTANT | Movement / Teleport | Teleports entity, Plays spell SFX |

---

## School: EVOCATION

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **ArrowVolleySpell** | irons_spellbooks:arrowvolley | LONG | Raycast / Targeted | Instantiates ArrowVolleyEntity, Applies spell damage |
| **ChainCreeperSpell** | irons_spellbooks:chaincreeper | LONG | Raycast / Targeted | Applies spell damage |
| **FangStrikeSpell** | irons_spellbooks:fangstrike | LONG | Summoning | Applies spell damage |
| **FangWardSpell** | irons_spellbooks:fangward | LONG | Summoning | Applies spell damage |
| **FirecrackerSpell** | irons_spellbooks:firecracker | INSTANT | Raycast / Targeted | Applies spell damage |
| **GustSpell** | irons_spellbooks:gust | LONG | Raycast / Targeted | Teleports entity |
| **InvisibilitySpell** | irons_spellbooks:invisibility | LONG | Buff / Debuff | Grants status effect |
| **LobCreeperSpell** | irons_spellbooks:lobcreeper | INSTANT | Projectile | Instantiates CreeperHeadProjectile, Applies spell damage |
| **ShieldSpell** | irons_spellbooks:shield | INSTANT | Raycast / Targeted | Instantiates ShieldEntity, Teleports entity |
| **SlowSpell** | irons_spellbooks:slow | LONG | Raycast / Targeted | Grants status effect |
| **SpectralHammerSpell** | irons_spellbooks:spectralhammer | INSTANT | Raycast / Targeted | Teleports entity |
| **SummonHorseSpell** | irons_spellbooks:summonhorse | LONG | Movement / Teleport | Teleports entity |
| **SummonVexSpell** | irons_spellbooks:summonvex | LONG | Summoning | Executes custom spell logic |
| **ThrowSpell** | irons_spellbooks:throw | LONG | Projectile | Instantiates ThrownItemProjectile, Applies spell damage, Teleports entity |
| **WololoSpell** | irons_spellbooks:wololo | LONG | Raycast / Targeted | Executes custom spell logic |

---

## School: FIRE

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **BlazeStormSpell** | irons_spellbooks:blazestorm | CONTINUOUS | Utility / Effect | Executes custom spell logic |
| **BurningDashSpell** | irons_spellbooks:burningdash | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect, Teleports entity |
| **FireArrowSpell** | irons_spellbooks:firearrow | LONG | Projectile | Instantiates FireArrowProjectile, Applies spell damage, Teleports entity |
| **FireballSpell** | irons_spellbooks:fireball | LONG | Movement / Teleport | Applies spell damage, Teleports entity |
| **FireboltSpell** | irons_spellbooks:firebolt | INSTANT | Projectile | Instantiates FireboltProjectile, Applies spell damage, Teleports entity |
| **FireBreathSpell** | irons_spellbooks:firebreath | CONTINUOUS | Projectile | Instantiates FireBreathProjectile, Applies spell damage, Teleports entity |
| **FlamingBarrageSpell** | irons_spellbooks:flamingbarrage | INSTANT | Movement / Teleport | Applies spell damage, Teleports entity |
| **FlamingStrikeSpell** | irons_spellbooks:flamingstrike | LONG | Summoning | Applies spell damage |
| **HeatSurgeSpell** | irons_spellbooks:heatsurge | LONG | Raycast / Targeted | Grants status effect |
| **MagmaBombSpell** | irons_spellbooks:magmabomb | LONG | Movement / Teleport | Applies spell damage, Teleports entity |
| **RaiseHellSpell** | irons_spellbooks:raisehell | LONG | Raycast / Targeted | Applies spell damage |
| **ScorchSpell** | irons_spellbooks:scorch | LONG | Area of Effect (AoE) | Applies spell damage, Plays spell SFX |
| **WallOfFireSpell** | irons_spellbooks:walloffire | INSTANT | Utility / Effect | Executes custom spell logic |

---

## School: HOLY

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **AngelWingsSpell** | irons_spellbooks:angelwings | INSTANT | Buff / Debuff | Grants status effect |
| **BlessingOfLifeSpell** | irons_spellbooks:blessingoflife | LONG | Raycast / Targeted | Executes custom spell logic |
| **CleanseSpell** | irons_spellbooks:cleanse | LONG | Area of Effect (AoE) | Executes custom spell logic |
| **CloudOfRegenerationSpell** | irons_spellbooks:cloudofregeneration | CONTINUOUS | Area of Effect (AoE) | Executes custom spell logic |
| **DivineSmiteSpell** | irons_spellbooks:divinesmite | LONG | Raycast / Targeted | Applies spell damage |
| **FortifySpell** | irons_spellbooks:fortify | LONG | Area of Effect (AoE) | Grants status effect |
| **GreaterHealSpell** | irons_spellbooks:greaterheal | LONG | Utility / Effect | Executes custom spell logic |
| **GuidingBoltSpell** | irons_spellbooks:guidingbolt | INSTANT | Projectile | Instantiates GuidingBoltProjectile, Applies spell damage, Teleports entity |
| **HasteSpell** | irons_spellbooks:haste | LONG | Raycast / Targeted | Grants status effect |
| **HealingCircleSpell** | irons_spellbooks:healingcircle | LONG | Raycast / Targeted | Applies spell damage, Teleports entity |
| **HealSpell** | irons_spellbooks:heal | INSTANT | Summoning | Executes custom spell logic |
| **SunbeamSpell** | irons_spellbooks:sunbeam | INSTANT | Raycast / Targeted | Instantiates SunbeamEntity, Applies spell damage, Plays spell SFX |
| **WispSpell** | irons_spellbooks:wisp | LONG | Raycast / Targeted | Instantiates WispEntity, Teleports entity |

---

## School: ICE

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **ConeOfColdSpell** | irons_spellbooks:coneofcold | CONTINUOUS | Projectile | Instantiates ConeOfColdProjectile, Applies spell damage, Teleports entity |
| **FrostbiteSpell** | irons_spellbooks:frostbite | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect |
| **FrostStepSpell** | irons_spellbooks:froststep | INSTANT | Movement / Teleport | Applies spell damage, Teleports entity, Plays spell SFX |
| **FrostwaveSpell** | irons_spellbooks:frostwave | LONG | Raycast / Targeted | Grants status effect |
| **IceBlockSpell** | irons_spellbooks:iceblock | LONG | Projectile | Instantiates IceBlockProjectile, Applies spell damage |
| **IceSpikesSpell** | irons_spellbooks:icespikes | INSTANT | Raycast / Targeted | Instantiates IceSpikeEntity, Applies spell damage |
| **IceTombSpell** | irons_spellbooks:icetomb | INSTANT | Utility / Effect | Instantiates IceTombEntity |
| **IcicleSpell** | irons_spellbooks:icicle | INSTANT | Projectile | Instantiates IcicleProjectile, Applies spell damage, Teleports entity |
| **RayOfFrostSpell** | irons_spellbooks:rayoffrost | INSTANT | Raycast / Targeted | Instantiates RayOfFrostVisualEntity, Applies spell damage |
| **SnowballSpell** | irons_spellbooks:snowball | LONG | Movement / Teleport | Applies spell damage, Teleports entity |
| **SummonPolarBearSpell** | irons_spellbooks:summonpolarbear | LONG | Movement / Teleport | Applies spell damage, Teleports entity |

---

## School: LIGHTNING

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **AscensionSpell** | irons_spellbooks:ascension | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect, Teleports entity |
| **BallLightningSpell** | irons_spellbooks:balllightning | INSTANT | Movement / Teleport | Applies spell damage, Teleports entity |
| **ChainLightningSpell** | irons_spellbooks:chainlightning | INSTANT | Raycast / Targeted | Applies spell damage |
| **ChargeSpell** | irons_spellbooks:charge | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect |
| **ElectrocuteSpell** | irons_spellbooks:electrocute | CONTINUOUS | Projectile | Instantiates ElectrocuteProjectile, Applies spell damage, Teleports entity |
| **LightningBoltSpell** | irons_spellbooks:lightningbolt | INSTANT | Raycast / Targeted | Applies spell damage, Teleports entity |
| **LightningLanceSpell** | irons_spellbooks:lightninglance | LONG | Projectile | Instantiates LightningLanceProjectile, Applies spell damage, Teleports entity |
| **ShockwaveSpell** | irons_spellbooks:shockwave | LONG | Summoning | Applies spell damage |
| **ThunderStepSpell** | irons_spellbooks:thunderstep | INSTANT | Projectile | Instantiates ThunderstepProjectile |
| **ThunderstormSpell** | irons_spellbooks:thunderstorm | LONG | Buff / Debuff | Grants status effect |
| **VoltStrikeSpell** | irons_spellbooks:voltstrike | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect, Teleports entity |

---

## School: NATURE

| Class Name | Spell ID | Cast Type | Mechanic | onCast Property & Behavior Summary |
| :--- | :--- | :--- | :--- | :--- |
| **AcidOrbSpell** | irons_spellbooks:acidorb | LONG | Movement / Teleport | Teleports entity |
| **BlightSpell** | irons_spellbooks:blight | LONG | Raycast / Targeted | Grants status effect |
| **EarthquakeSpell** | irons_spellbooks:earthquake | LONG | Raycast / Targeted | Applies spell damage |
| **FireflySwarmSpell** | irons_spellbooks:fireflyswarm | LONG | Projectile | Instantiates FireflySwarmProjectile, Applies spell damage |
| **GluttonySpell** | irons_spellbooks:gluttony | INSTANT | Buff / Debuff | Grants status effect |
| **OakskinSpell** | irons_spellbooks:oakskin | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect |
| **PoisonArrowSpell** | irons_spellbooks:poisonarrow | LONG | Movement / Teleport | Applies spell damage, Teleports entity |
| **PoisonBreathSpell** | irons_spellbooks:poisonbreath | CONTINUOUS | Projectile | Instantiates PoisonBreathProjectile, Applies spell damage, Teleports entity |
| **PoisonSplashSpell** | irons_spellbooks:poisonsplash | LONG | Raycast / Targeted | Applies spell damage |
| **RootSpell** | irons_spellbooks:root | LONG | Raycast / Targeted | Instantiates RootEntity, Grants status effect |
| **SpiderAspectSpell** | irons_spellbooks:spideraspect | INSTANT | Buff / Debuff | Applies spell damage, Grants status effect |
| **StompSpell** | irons_spellbooks:stomp | LONG | Movement / Teleport | Applies spell damage, Teleports entity |
| **TouchDigSpell** | irons_spellbooks:touchdig | INSTANT | Raycast / Targeted | Executes custom spell logic |

---

