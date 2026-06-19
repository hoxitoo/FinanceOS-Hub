package com.financeos.hub.di

import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.banks.AlfabankParser
import com.financeos.hub.core.parser.banks.GazprombankParser
import com.financeos.hub.core.parser.banks.MtsBankParser
import com.financeos.hub.core.parser.banks.OtkritieParser
import com.financeos.hub.core.parser.banks.PostaBankParser
import com.financeos.hub.core.parser.banks.RaiffeisenParser
import com.financeos.hub.core.parser.banks.RosbankParser
import com.financeos.hub.core.parser.banks.RosselkhozParser
import com.financeos.hub.core.parser.banks.SberbankParser
import com.financeos.hub.core.parser.banks.TbankParser
import com.financeos.hub.core.parser.banks.VtbParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {
    // P1 banks
    @Binds @IntoSet abstract fun bindSberbank(p: SberbankParser)  : BankParser
    @Binds @IntoSet abstract fun bindTbank(p: TbankParser)         : BankParser
    @Binds @IntoSet abstract fun bindVtb(p: VtbParser)             : BankParser
    @Binds @IntoSet abstract fun bindAlfa(p: AlfabankParser)       : BankParser
    @Binds @IntoSet abstract fun bindGazprom(p: GazprombankParser) : BankParser
    // P2 banks
    @Binds @IntoSet abstract fun bindRaiffeisen(p: RaiffeisenParser): BankParser
    @Binds @IntoSet abstract fun bindRosbank(p: RosbankParser)      : BankParser
    @Binds @IntoSet abstract fun bindOtkritie(p: OtkritieParser)    : BankParser
    // P3 banks
    @Binds @IntoSet abstract fun bindMtsBank(p: MtsBankParser)       : BankParser
    @Binds @IntoSet abstract fun bindPostaBank(p: PostaBankParser)   : BankParser
    @Binds @IntoSet abstract fun bindRosselkhoz(p: RosselkhozParser) : BankParser
}
