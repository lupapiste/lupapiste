LUPAPISTE.HandlerListModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.handlerService;

  self.handlers = service.getApplicationHandlers;

  self.editHandlers = _.partial( hub.send,
                                 "cardStack::select",
                                 {card: "edit-handler",
                                  stack: "summary"});

};
