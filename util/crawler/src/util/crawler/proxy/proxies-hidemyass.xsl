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
        <xsl:for-each select="$t//tr[td[matches(., '\d+\.\d+\.\d+\.\d+', 'i')]]">
          <xsl:variable name="tr" select="."/>
          <xsl:analyze-string regex="(\d+\.\d+\.\d+\.\d+)"
                              select="td[matches(., '\d+\.\d+\.\d+\.\d+', 'i')]">
            <xsl:matching-substring>
              <proxy>
                <ip>
                  <xsl:value-of select="regex-group(1)"/>
                </ip>
                <port>
                  <xsl:value-of select="$tr/td[matches(., '\d+\.\d+\.\d+\.\d+', 'i')]/following-sibling::td[1]/normalize-space()"/>
                </port>
              </proxy>
            </xsl:matching-substring>
          </xsl:analyze-string>
        </xsl:for-each>
      </xsl:if>
    </proxies>
  </xsl:template>
  
  
</xsl:transform>
 