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
        <xsl:for-each select="$t//td/text()">
          <xsl:analyze-string regex="(\d+\.\d+\.\d+\.\d+):(\d+)"
                              select=".">
            <xsl:matching-substring>
              <proxy>
                <ip>
                  <xsl:value-of select="regex-group(1)"/>
                </ip>
                <port>
                  <xsl:value-of select="regex-group(2)"/>
                </port>
              </proxy>
            </xsl:matching-substring>
          </xsl:analyze-string>
        </xsl:for-each>
      </xsl:if>
    </proxies>
  </xsl:template>
  
</xsl:transform>
