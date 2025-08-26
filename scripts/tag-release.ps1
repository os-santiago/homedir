param(
  [Parameter(Mandatory=$true)][string]$version
)

$tag = $version

git tag -a $tag -m "EventFlow $version"
git push origin $tag
