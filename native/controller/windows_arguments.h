#pragma once
#include <string>
inline std::wstring QuoteWindowsArgument(const std::wstring& value) {
    std::wstring out=L"\"";std::size_t slashes=0;
    for(wchar_t c:value){
        if(c==L'\\'){++slashes;continue;}
        if(c==L'"'){out.append(slashes*2+1,L'\\');out+=c;}else{out.append(slashes,L'\\');out+=c;}
        slashes=0;
    }
    out.append(slashes*2,L'\\');out+=L'"';return out;
}
