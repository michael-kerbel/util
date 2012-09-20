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
    <xsl:variable name="root" select="/"/>
    <proxies>
      <xsl:variable name="t" select="//table[@id='listtable']"/>
      <xsl:if test="$t">
        <xsl:variable name="displayedStyles">
          <xsl:variable name="styleLines" select="//style/tokenize(text(), $newline)"/>
          <xsl:for-each select="$styleLines">
            <xsl:if test="contains(., 'inline')">|<xsl:value-of select="replace(., '\.(.*)\{.*', '$1')"/>|</xsl:if>
          </xsl:for-each>
        </xsl:variable>
        
        <xsl:for-each select="$t//tr
                              [matches(.//div[@class='speedbar response_time']/@rel, '^[12345]\d\d\d$', 'i') ]
                              [matches(.//div[@class='speedbar connection_time']/@rel, '^[12345]\d\d\d$', 'i') or matches(.//div[@class='speedbar connection_time']/@rel, '^\d{1,3}$', 'i') ]
                              ">
          <xsl:variable name="tr" select="."/>
          <xsl:analyze-string regex="(\d+\.\d+\.\d+\.\d+)"
                              select="string-join(td/span//text()[not(../@style='display:none') and (not(../@class) or number(../@class) = number(../@class) or contains($displayedStyles, ../@class))], '')">
            <xsl:matching-substring>
              <proxy>
                <ip>
                  <xsl:value-of select="regex-group(1)"/>
                </ip>
                <port>
                  <xsl:value-of select="$tr/td[matches(normalize-space(.), '^\d{2,5}$', 'i')]/normalize-space()"/>
                </port>
                <responseTime>
                  <xsl:value-of select="$tr//div[@class='speedbar response_time']/@rel"/>
                </responseTime>
                <connectionTime>
                  <xsl:value-of select="$tr//div[@class='speedbar connection_time']/@rel"/>
                </connectionTime>
              </proxy>
            </xsl:matching-substring>
          </xsl:analyze-string>
        </xsl:for-each>
      </xsl:if>
    </proxies>
  </xsl:template>
  
  
</xsl:transform>
