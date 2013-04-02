/**
 * Lupapiste Modal Window module.
 * The modal container element must have 'window' CSS class.
 */
if (typeof LUPAPISTE === "undefined") {
  var LUPAPISTE = {};
}

/**
 * Modal window prototype.
 * @param {String}  Mask element ID. Mask will be created automatically.
 * @param {String}  Mask color: 'black' or 'white'
 */
LUPAPISTE.Modal = function(maskId, maskColor) {
  "use strict";

  var self = this;
  self.mask = undefined;
  self.maskId = maskId;
  self.maskColor = maskColor;

  self.createMask = function() {
    if (!document.getElementById(self.maskId)) {
      var maskDiv = document.createElement("div");
      maskDiv.id = self.maskId;
      maskDiv.className = "mask " + self.maskColor;
      document.body.appendChild(maskDiv);
    }
    self.mask = $('#' + self.maskId);
    self.mask.click(self.close);
  };

  self.getMask = function() {
    return self.mask;
  };

  /**
   * Opens a modal window.
   * @param {String}  Modal window container jQuery selector
   */
  self.open = function(selector) {
    var maskHeight = $(document).height();
    var maskWidth = $(window).width();
    self.mask.css({'width':maskWidth,'height':maskHeight});
    self.mask.fadeIn(300);
    self.mask.fadeTo("fast",0.8);

    var winHeight = $(window).height();
    var winWidth = $(window).width();
    $(selector)
      .css('top',  winHeight/2-$(selector).height()/2)
      .css('left', winWidth/2-$(selector).width()/2)
      .fadeIn(600);

    var inputs = $(selector + ' input:enabled');
    if(inputs) {
      inputs[0].focus();
    }
    
    return false;
  };

  self.close = function(e) {
    if (e && typeof e.preventDefault === "function") {
      e.preventDefault();
    }
    $('.window:visible').each(function() {
      hub.send("dialog-close", {id : $(this).attr('id')});
    });
    $('#' + self.maskId + ', .window').hide();
  };

};

LUPAPISTE.Modal.YesNoTemplate = '<div class="window autosized-yes-no">' +
  '<div class="dialog-header"><p class="dialog-title"></p><p class="dialog-close close">X</p></div>' +
  '<div class="dialog-content"><p></p>' +
  '<button class="btn btn-primary btn-dialog close" data-test-id="confirm-yes"></button>' +
  '<button class="btn btn-dialog close" data-test-id="confirm-no"></button></div></div>';

/**
 * Lupapiste Modal Dialog window.
 * Call LUPAPISTE.ModalDialog.init() to activate.
 */
LUPAPISTE.ModalDialog = new LUPAPISTE.Modal("ModalDialogMask", "black");
LUPAPISTE.ModalDialog.dynamicDialogs = [];

LUPAPISTE.ModalDialog.newYesNoDialog = function(id, title, content, yesTitle, yesHandler, noTitle, noHandler) {
  "use strict";
  var dialog$ = $(LUPAPISTE.Modal.YesNoTemplate).attr("id", id);
  dialog$.find(".dialog-title").text(title);
  dialog$.find(".dialog-content p").text(content);
  dialog$.find("[data-test-id='confirm-yes']").click(yesHandler).text(yesTitle);
  dialog$.find("[data-test-id='confirm-no']").text(noTitle);
  if (noHandler) {
    dialog$.find("[data-test-id='confirm-no']").click(noHandler);
  }
  LUPAPISTE.ModalDialog.dynamicDialogs.push(dialog$);
  return dialog$;
};

/**
 * Initializes modal dialog elements
 */
LUPAPISTE.ModalDialog.init = function() {
  "use strict";

  this.createMask();

  _.each(LUPAPISTE.ModalDialog.dynamicDialogs, function(d) {
    if (!document.getElementById(d.attr("id"))) {
      $("body").append(d);
    }
  });

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

/**
 * Lupapiste Modal Progress Bar window.
 * Call LUPAPISTE.ModalProgress.init() to setup and show() to activate.
 */
LUPAPISTE.ModalProgress = new LUPAPISTE.Modal("ModalProgressMask", "white");
LUPAPISTE.ModalProgress.progressBarId = "ModalProgressBar";

LUPAPISTE.ModalProgress.init = function() {
  "use strict";

  this.createMask();

  // Create progress bar
  if (!document.getElementById(this.progressBarId)) {
    var progressBarContainer = document.createElement("div");
    progressBarContainer.id = LUPAPISTE.ModalProgress.progressBarId;
    progressBarContainer.className = "window rounded";
    progressBarContainer.style.textAlign = "center";
    progressBarContainer.style.padding = "0";
    progressBarContainer.style.lineHeight = "0";

    var progressBarImg = document.createElement("img");
    progressBarImg.src = "/img/loader-bar.gif";
    progressBarImg.alt = "...";
    progressBarImg.width = 220;
    progressBarImg.height = 19;
    progressBarContainer.appendChild(progressBarImg);
    document.body.appendChild(progressBarContainer);
  }
};

LUPAPISTE.ModalProgress.show = function() {
  "use strict";

  this.open("#" + LUPAPISTE.ModalProgress.progressBarId);
  this.getMask().unbind('click');
};
