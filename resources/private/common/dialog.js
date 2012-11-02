/**
 * Lupapiste Modal Window module.
 * Call LUPAPISTE.ModalDialog.init() to activate.
 */
if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

LUPAPISTE.ModalDialog = {};

/**
 * Opens a modal window.
 * @param {String}  Modal window container jQuery selector
 */
LUPAPISTE.ModalDialog.open = function(selector) {
  var maskHeight = $(document).height();
  var maskWidth = $(window).width();
  $('#mask').css({'width':maskWidth,'height':maskHeight});
  $('#mask').fadeIn(300);
  $('#mask').fadeTo("fast",0.8);
  var winHeight = $(window).height();
  var winWidth = $(window).width();
  $(selector).css('top',  winHeight/2-$(selector).height()/2);
  $(selector).css('left', winWidth/2-$(selector).width()/2);
  $(selector).fadeIn(600);
  return false;
}

LUPAPISTE.ModalDialog.close = function(e) {
  if (e) {
    e.preventDefault();
  }
  $('#mask, .window').hide();
}

/**
 * Initializes modal dialog elements
 */
LUPAPISTE.ModalDialog.init = function() {

  // Create mask element
  maskDiv = document.createElement("div");
  maskDiv.id = "mask";
  document.body.appendChild(maskDiv);

  // Register default opener:
  // Click any element that has .modal class and data-windows-id that
  // references to modal window container element ID.
  $(".modal").click(function (e) {
   e.preventDefault();
   var id = $(this).attr('data-window-id');
   if (id) {
     LUPAPISTE.ModalDialog.open("#" + id);
   } else {
     warn("No 'data-window-id' attribute");
   }
   return false;
  });

  // Register modal window closing handlers
  $('.window .close').click(LUPAPISTE.ModalDialog.close);
  $('#mask').click(LUPAPISTE.ModalDialog.close);
}
