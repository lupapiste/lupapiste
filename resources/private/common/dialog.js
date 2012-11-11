/**
 * Lupapiste Modal Window module.
 * The modal container element must have 'window' CSS class.
 */
if (typeof LUPAPISTE == "undefined") {var LUPAPISTE = {};}

/**
 * Modal window prototype.
 * @param {String}  Mask element ID. Mask will be created automatically.
 * @param {String}  Mask color: 'black' or 'white'
 */
LUPAPISTE.Modal = function(maskId, maskColor) {
  var self = this;
  this.mask = undefined;
  this.maskId = maskId;
  this.maskColor = maskColor;

  this.createMask = function() {
    if (!document.getElementById(self.maskId)) {
      maskDiv = document.createElement("div");
      maskDiv.id = LUPAPISTE.ModalDialog.maskId;
      maskDiv.className = "mask " + self.maskColor;
      document.body.appendChild(maskDiv);
    }
    self.mask = $('#' + self.maskId);
    self.mask.click(this.close);
  };

  /**
   * Opens a modal window.
   * @param {String}  Modal window container jQuery selector
   */
  this.open = function(selector) {
    var maskHeight = $(document).height();
    var maskWidth = $(window).width();
    self.mask.css({'width':maskWidth,'height':maskHeight});
    self.mask.fadeIn(300);
    self.mask.fadeTo("fast",0.8);

    var winHeight = $(window).height();
    var winWidth = $(window).width();
    $(selector).css('top',  winHeight/2-$(selector).height()/2);
    $(selector).css('left', winWidth/2-$(selector).width()/2);
    $(selector).fadeIn(600);
    return false;
  };

  this.close = function(e) {
    if (e && typeof e.preventDefault === "function") {
      e.preventDefault();
    }
    $('.window:visible').each(function() {
      hub.send("dialog-close", {id : $(this).attr('id')});
    });
    $('#' + self.maskId + ', .window').hide();
  };

};

/**
 * Lupapiste Modal Dialog window.
 * Call LUPAPISTE.ModalDialog.init() to activate.
 */
LUPAPISTE.ModalDialog = new LUPAPISTE.Modal("ModalDialogMask", "black");

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
  $('.window .close').click(this.close);

};

