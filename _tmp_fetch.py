import requests
print('start')
url='https://homedir.my.canva.site/'
r=requests.get(url, timeout=20, verify=False)
print('status', r.status_code)
open('quarkus-app/src/main/resources/META-INF/resources/site-beta/index-remote.html','wb').write(r.content)
print('saved', len(r.content))
