# Copyright (c) 2000-2019, Board of Trustees of Leland Stanford Jr. University
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice,
# this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
# this list of conditions and the following disclaimer in the documentation
# and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its contributors
# may be used to endorse or promote products derived from this software without
# specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
# CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
# SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
# INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
# CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.

#
# SERVICE-LEVEL
#

# None

#
# SYSTEM-LEVEL
#

# Enable crawls of plugin registries only
org.lockss.crawler.enabled=true
org.lockss.daemon.crawlMode=NonPlugins
# if false this will turn off all pluggable crawlers
org.lockss.crawlerservice.pluggableEnabled=true
# The path to the pluggable crawlmanager db
org.lockss.crawlerservice.dbPath=./crawlerDb

# The list of supported crawlers
org.lockss.crawlerservice.crawlers=classic;wget

# The lockss/default crawler
org.lockss.crawlerservice.classic.enabled=true

# The wget crawler
org.lockss.crawlerservice.wget.enabled=true
org.lockss.crawlerservice.wget.crawler=org.lockss.laaws.crawler.wget.WgetCmdLineCrawler
org.lockss.crawlerservice.wget.opt.wait=5
org.lockss.crawlerservice.wget.successCode=0;8
org.lockss.crawlerservice.wget.opt.warc-keep-log=off
