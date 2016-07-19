;(function() {
  "use strict";

  var components = [
    {name: "calendar-view"},
    {name: "reservation-slot-create-bubble"},
    {name: "reservation-slot-edit-bubble"},
    {name: "application-authority-calendar"}
  ];

  $(_.partial(ko.registerLupapisteComponents, components));

})();
