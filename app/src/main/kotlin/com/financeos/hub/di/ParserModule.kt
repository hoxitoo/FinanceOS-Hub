package com.financeos.hub.di

import com.financeos.hub.core.parser.BankParser
import com.financeos.hub.core.parser.banks.AlfabankParser
import com.financeos.hub.core.parser.banks.GazprombankParser
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
    @Binds @IntoSet abstract fun bindSberbank(p: SberbankParser): BankParser
    @Binds @IntoSet abstract fun bindTbank(p: TbankParser): BankParser
    @Binds @IntoSet abstract fun bindVtb(p: VtbParser): BankParser
    @Binds @IntoSet abstract fun bindAlfa(p: AlfabankParser): BankParser
    @Binds @IntoSet abstract fun bindGazprom(p: GazprombankParser): BankParser
}
