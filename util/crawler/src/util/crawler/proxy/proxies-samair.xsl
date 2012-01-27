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
        <xsl:for-each select="$t//td">
          <xsl:analyze-string regex="(\d+\.\d+\.\d+\.\d+)document\.write\(...(\+.+)+\)"
                              select=".">
            <xsl:matching-substring>
              <proxy>
                <ip>
                  <xsl:value-of select="regex-group(1)"/>
                </ip>
                <port>
                  <xsl:variable name="port" select="replace(regex-group(2),'\+','')"/>
                  <xsl:variable name="port" select="replace($port,'a',f:getsub('a',$root))"/>
                  <xsl:variable name="port" select="replace($port,'b',f:getsub('b',$root))"/>
                  <xsl:variable name="port" select="replace($port,'c',f:getsub('c',$root))"/>
                  <xsl:variable name="port" select="replace($port,'d',f:getsub('d',$root))"/>
                  <xsl:variable name="port" select="replace($port,'e',f:getsub('e',$root))"/>
                  <xsl:variable name="port" select="replace($port,'f',f:getsub('f',$root))"/>
                  <xsl:variable name="port" select="replace($port,'g',f:getsub('g',$root))"/>
                  <xsl:variable name="port" select="replace($port,'h',f:getsub('h',$root))"/>
                  <xsl:variable name="port" select="replace($port,'i',f:getsub('i',$root))"/>
                  <xsl:variable name="port" select="replace($port,'j',f:getsub('j',$root))"/>
                  <xsl:variable name="port" select="replace($port,'k',f:getsub('k',$root))"/>
                  <xsl:variable name="port" select="replace($port,'l',f:getsub('l',$root))"/>
                  <xsl:variable name="port" select="replace($port,'m',f:getsub('m',$root))"/>
                  <xsl:variable name="port" select="replace($port,'n',f:getsub('n',$root))"/>
                  <xsl:variable name="port" select="replace($port,'o',f:getsub('o',$root))"/>
                  <xsl:variable name="port" select="replace($port,'p',f:getsub('p',$root))"/>
                  <xsl:variable name="port" select="replace($port,'q',f:getsub('q',$root))"/>
                  <xsl:variable name="port" select="replace($port,'r',f:getsub('r',$root))"/>
                  <xsl:variable name="port" select="replace($port,'s',f:getsub('s',$root))"/>
                  <xsl:variable name="port" select="replace($port,'t',f:getsub('t',$root))"/>
                  <xsl:variable name="port" select="replace($port,'u',f:getsub('u',$root))"/>
                  <xsl:variable name="port" select="replace($port,'v',f:getsub('v',$root))"/>
                  <xsl:variable name="port" select="replace($port,'w',f:getsub('w',$root))"/>
                  <xsl:variable name="port" select="replace($port,'x',f:getsub('x',$root))"/>
                  <xsl:variable name="port" select="replace($port,'y',f:getsub('y',$root))"/>
                  <xsl:variable name="port" select="replace($port,'z',f:getsub('z',$root))"/>
                  <xsl:value-of select="$port"/>
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
