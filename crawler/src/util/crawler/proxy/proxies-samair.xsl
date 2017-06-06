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
      <xsl:variable name="t" select="//table[@id='proxylist']"/>
      <xsl:if test="$t">
        <xsl:for-each select="//table[@id='proxylist']//td/span">
          <xsl:variable name="span" select="."/>
          <xsl:analyze-string regex="(\d+\.\d+\.\d+\.\d+):"
                              select=".">
            <xsl:matching-substring>
              <proxy>
                <ip>
                  <xsl:value-of select="regex-group(1)"/>
                </ip>
                <port>
                  <xsl:value-of select="$span/@class"/>
                </port>
              </proxy>
            </xsl:matching-substring>
          </xsl:analyze-string>
        </xsl:for-each>
      </xsl:if>
    </proxies>
  </xsl:template>
  
  
  <xsl:function name="f:getsub">
    <xsl:param name="char"/>
    <xsl:param name="root"/>
    <xsl:variable name="script" select="$root//script/text()[matches(., concat($char,'=\d'), 'i')]"/>
    <xsl:choose>
      <xsl:when test="$script">
        <xsl:value-of select="replace(normalize-space($script),concat('^.*',$char,'=(\d)','.*$'), '$1')"/>
      </xsl:when>
      <xsl:otherwise><xsl:text></xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>
  
</xsl:transform>
