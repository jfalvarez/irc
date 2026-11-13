package com.jfaf.irc.util

import com.jfaf.irc.BuildConfig
import com.jfaf.irc.domain.config.AppConfig
import com.jfaf.irc.domain.config.parseKeywordList

class BuildConfigAppConfig : AppConfig {
    override val imageFilterKeywords: Set<String> = parseKeywordList(BuildConfig.IMAGE_FILTER_KEYWORDS)
    override val googleSignInClientId: String = BuildConfig.GOOGLE_SIGN_IN_CLIENT_ID
}
