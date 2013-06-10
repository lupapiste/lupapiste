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
  self.open = function(arg) {
    var element = _.isString(arg) ? $(arg) : arg,
        elementWidth = element.width(),
        elementHeight = element.height(),
        winHeight = $(window).height(),
        winWidth = $(window).width(),
        maskHeight = $(document).height(),
        maskWidth = winWidth;

    self.mask
      .css({"width": maskWidth, "height": maskHeight})
      .fadeIn(300)
      .fadeTo("fast", 0.8);

    element
      .css("top",  winHeight / 2 - elementHeight / 2)
      .css("left", winWidth / 2 - elementWidth / 2)
      .fadeIn(600)
      .find(".close")
        .click(self.close)
        .end()
      .find("input:enabled")
        .first()
        .focus();

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

LUPAPISTE.ModalDialog.setDialogContent = function(dialog$, title, content, yesTitle, yesHandler, noTitle, noHandler) {
  function bindButton(elem$, text, f) {
    elem$.unbind("click").text(text);
    if (f) {
      elem$.click(f);
    }
  }
  
  dialog$.find(".dialog-title").text(title);
  dialog$.find(".dialog-content p").text(content);
  bindButton(dialog$.find("[data-test-id='confirm-yes']"), yesTitle, yesHandler);
  bindButton(dialog$.find("[data-test-id='confirm-no']"), noTitle, noHandler);
}

LUPAPISTE.ModalDialog.newYesNoDialog = function(id, title, content, yesTitle, yesHandler, noTitle, noHandler) {
  "use strict";
  var dialog$ = $(LUPAPISTE.Modal.YesNoTemplate).attr("id", id);
  LUPAPISTE.ModalDialog.setDialogContent(dialog$, title, content, yesTitle, yesHandler, noTitle, noHandler);  
  LUPAPISTE.ModalDialog.dynamicDialogs.push(dialog$);
  return dialog$;
};

LUPAPISTE.ModalDialog.dynamicYesNoId = "dynamic-yes-no-confirm-dialog";
LUPAPISTE.ModalDialog.newYesNoDialog(LUPAPISTE.ModalDialog.dynamicYesNoId);

LUPAPISTE.ModalDialog.showDynamicYesNo = function(title, content, yesTitle, yesHandler, noTitle, noHandler) {
  "use strict";
  var dialog$ = $("#" + LUPAPISTE.ModalDialog.dynamicYesNoId);
  LUPAPISTE.ModalDialog.setDialogContent(dialog$, title, content, yesTitle, yesHandler, noTitle, noHandler);  
  LUPAPISTE.ModalDialog.open(dialog$);  
  return dialog$;
}

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
    }
    return false;
  });
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
