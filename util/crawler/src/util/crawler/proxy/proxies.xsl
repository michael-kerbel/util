<?xml version="1.0" encoding="UTF-8"?>
<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 xmlns:xs="http://www.w3.org/2001/XMLSchema"
 xmlns:f="http://localhost/functions"
 exclude-result-prefixes="xs f"
 version="2.0">
  
  <xsl:param name="linklabel"/>
  <xsl:param name="newline"/>
  
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="/">
    <proxies>
      <xsl:variable name="t" select="//table[@class='tablelist']"/>
      <xsl:if test="$t">
        <xsl:for-each select="$t//td/script/../text()">
          <xsl:variable name="port" select="replace(replace(../script,'document.write\(&quot;:&quot;',''),'[+)]','')"/>
          <proxy>
            <ip>
              <xsl:value-of select="."/>
            </ip>
            <port>
							<xsl:value-of select="$port"/>
            </port>
            <portTranslation>
              <xsl:analyze-string regex="(.)=(\d);"
                                  select="//head/script[2]">
                <xsl:matching-substring>
                  <xsl:value-of select="regex-group(1)"/><xsl:value-of select="'='"/><xsl:value-of select="regex-group(2)"/><xsl:value-of select="$newline"/>
                </xsl:matching-substring>
              </xsl:analyze-string>
            </portTranslation>
          </proxy>
        </xsl:for-each>
      </xsl:if>
    </proxies>
  </xsl:template>
  
</xsl:transform>
