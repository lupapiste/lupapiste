/**
 * Lupapiste Modal Window module.
 */
if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

/**
 * Modal window prototype.
 * @param {String}  Mask element ID. Mask will be created automatically.
 * @param {String}  Mask color: 'black' or 'white'
 * @param {String}  Modal window container jQuery selector
 */
LUPAPISTE.Modal = function(maskId, maskColor, contentId) {
  this.mask = undefined;
  this.maskId = maskId;
  this.maskColor = maskColor;
  this.contentId = contentId;

  this.createMask = function() {
    if (!this.mask) {
      if (!document.getElementById(this.maskId)) {
        maskDiv = document.createElement("div");
        maskDiv.id = LUPAPISTE.ModalDialog.maskId;
        maskDiv.className = "mask " + this.maskColor;
        document.body.appendChild(maskDiv);
      }
      this.mask = $('#' + this.maskId);
    }
  }

  this.getMask = function() {
    if (!this.mask) {
      this.createMask();
    }
    return this.mask;
  }



};

/**
 * Call LUPAPISTE.ModalDialog.init() to activate.
 */
LUPAPISTE.ModalDialog = new LUPAPISTE.Modal("ModalDialogMask", "black", undefined);

/**
 * Opens a modal window.
 * @param {String}  Modal window container jQuery selector
 */
LUPAPISTE.ModalDialog.open = function(selector) {
  var maskHeight = $(document).height();
  var maskWidth = $(window).width();
  var mask = LUPAPISTE.ModalDialog.getMask();
  mask.css({'width':maskWidth,'height':maskHeight});
  mask.fadeIn(300);
  mask.fadeTo("fast",0.8);
  var winHeight = $(window).height();
  var winWidth = $(window).width();
  $(selector).css('top',  winHeight/2-$(selector).height()/2);
  $(selector).css('left', winWidth/2-$(selector).width()/2);
  $(selector).fadeIn(600);
  return false;
};

LUPAPISTE.ModalDialog.close = function(e) {
  if (e && typeof e.preventDefault === "function") {
    e.preventDefault();
  }
  $('.window').each(function() {
    var dialog = $(this);
    var dialogId = dialog.attr('id');
    if (dialog.is(":visible")) {
      hub.send("dialog-close", {id : dialogId});
    }
  });
  $('#' + LUPAPISTE.ModalDialog.maskId + ', .window').hide();
};

/**
 * Initializes modal dialog elements
 */
LUPAPISTE.ModalDialog.init = function() {

  // Create mask element
  this.createMask();

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
  LUPAPISTE.ModalDialog.getMask().click(LUPAPISTE.ModalDialog.close);
};
