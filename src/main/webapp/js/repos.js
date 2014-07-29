
function showRepos(data) {
  for(var i=0; i<data.length; i++) {
    var repo = data[i];

    var repoBtn = $("<a />")
      .attr("class", "list-group-item")
      .text(repo["name"])
      .attr("data-url", repo["url"])
      .on("click", function() {
        $("#repoList .list-group-item").removeClass("active");
        $(this).addClass("active");
      })
      .prependTo($("#repoList"));
  }

  $("#selectRepo").show();
}

function getRepoUrl() {
  var textInput = $("#inputRepoUrl");
  if(textInput.val() != "") {
    return textInput.val();
  }
  else {
    var active = $("#repoList .list-group-item.active");
    if(active.length > 0)
      return active.data("url");
    else
      return null;
  }
}