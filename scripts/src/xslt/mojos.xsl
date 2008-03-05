<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="text" name="textFormat" />
<xsl:template match="/">
  <xsl:for-each select="/plugin/mojos/mojo">
    <xsl:variable name="mojo" select="goal" />
    <xsl:result-document href="pax-{$mojo}.properties" format="textFormat">
      <xsl:for-each select="configuration/*[not(starts-with(., 'm_'))]">
        <xsl:if test="matches(., '^\$\{[a-zA-Z]+\}$')">
          <xsl:value-of select="replace(., '[${}]', '')" />
<xsl:text>
</xsl:text>
        </xsl:if>
      </xsl:for-each>
    </xsl:result-document>
  </xsl:for-each>
</xsl:template>
</xsl:stylesheet>
