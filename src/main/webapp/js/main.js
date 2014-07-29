
$(document).ready(main);

function main() {
  setupLogin();
  setupPdfUpload();
  $("#uploadingDiv").hide();
}

function showAlert(type, text) {
  $(".alert").remove();

  var alert = $("<div/>")
    .attr("class", "alert alert-dismissible fade in alert-" + type)
    .attr("role", "alert")
    .html("<button type='button' class='close' data-dismiss='alert'><span aria-hidden='true'>&times;</span><span class='sr-only'>Close</span></button>" + text);
  
  alert.hide().appendTo($("#alerts")).fadeTo("medium", 1);
}