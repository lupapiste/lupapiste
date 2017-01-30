LUPAPISTE.HandlerListModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.handlerService;

  self.handlers = service.applicationHandlers;

  self.itemText = service.nameAndRoleString;

  self.editHandlers = _.partial( hub.send,
                                 "cardService::select",
                                 {card: "edit-handlers",
                                  deck: "summary"});

};
