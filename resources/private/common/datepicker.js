/*global jQuery:false */

jQuery(function($){
  "use strict";
  if ($.datepicker) {
    $.datepicker.regional.fi = {
        closeText: "Sulje",
        prevText: "«Edellinen",
        nextText: "Seuraava»",
        currentText: "Tänään",
        monthNames: ["Tammikuu","Helmikuu","Maaliskuu","Huhtikuu","Toukokuu","Kesäkuu","Heinäkuu","Elokuu","Syyskuu","Lokakuu","Marraskuu","Joulukuu"],
        monthNamesShort: ["Tammi","Helmi","Maalis","Huhti","Touko","Kesä","Heinä","Elo","Syys","Loka","Marras","Joulu"],
        dayNamesShort: ["Su","Ma","Ti","Ke","To","Pe","Su"],
        dayNames: ["Sunnuntai","Maanantai","Tiistai","Keskiviikko","Torstai","Perjantai","Lauantai"],
        dayNamesMin: ["Su","Ma","Ti","Ke","To","Pe","La"],
        weekHeader: "Vk",
        showWeek: true,
        dateFormat: "dd.mm.yy",
        firstDay: 1,
        isRTL: false,
        buttonImage: "/lp-static/img/calendar.gif",
        showMonthAfterYear: false,
        yearSuffix: ""
      };

    $.datepicker.regional.sv = {
        closeText: "Stäng",
        prevText: "«Förra",
        nextText: "Nästa»",
        currentText: "Idag",
        monthNames: ["Januari","Februari","Mars","April","Maj","Juni", "Juli","Augusti","September","Oktober","November","December"],
        monthNamesShort: ["Jan","Feb","Mar","Apr","Maj","Jun", "Jul","Aug","Sep","Okt","Nov","Dec"],
        dayNamesShort: ["Sön","Mån","Tis","Ons","Tor","Fre","Lör"],
        dayNames: ["Söndag","Måndag","Tisdag","Onsdag","Torsdag","Fredag","Lördag"],
        dayNamesMin: ["Sö","Må","Ti","On","To","Fr","Lö"],
        weekHeader: "Ve",
        dateFormat: "dd.mm.yy",
        firstDay: 1,
        isRTL: false,
        buttonImage: "/lp-static/img/calendar.gif",
        showMonthAfterYear: false,
        yearSuffix: ""
      };

    $.datepicker.setDefaults($.datepicker.regional[loc.getCurrentLanguage()]);
    $.datepicker.setDefaults({
      changeMonth: true,
      changeYear: true,
      numberOfMonths: 3,
      showButtonPanel: true
    });
  }
});
