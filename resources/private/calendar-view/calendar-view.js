;(function() {
  "use strict";

  var components = [
    {name: "calendar-view"},
    {name: "reservation-slot-create-bubble"},
    {name: "reservation-slot-edit-bubble"},
    {name: "application-authority-calendar"},
    {name: "reservation-slot-reserve-bubble"}
  ];

  $(_.partial(ko.registerLupapisteComponents, components));

})();
