package es.sebas1705.nanodemo.feature.lab.rag

/** A bundled document: id, title and Markdown body. */
class SampleDocument(val id: String, val title: String, val text: String)

/**
 * A small fictional company handbook (Nimbra) in Spanish so the RAG demo works with no setup. Every fact a
 * question asks for is a specific number or name that a model cannot guess, so the with/without-retrieval
 * comparison is meaningful. `questions` map each question to its document and an evidence phrase; the unit
 * tests check that every evidence phrase really is in its document.
 */
object SampleCorpus {

    val documents: List<SampleDocument> = listOf(
        SampleDocument(
            id = "sample-vacaciones",
            title = "Vacaciones y permisos",
            text = """
# Vacaciones y permisos

## Días de vacaciones
Cada persona empleada tiene derecho a 23 días laborables de vacaciones al año. Los días no disfrutados se pueden trasladar al año siguiente hasta el 31 de marzo, con un máximo de 5 días. Pasada esa fecha se pierden sin compensación económica.

## Cómo solicitarlas
Las vacaciones se solicitan en el portal de personas con al menos 15 días de antelación. Tu responsable las aprueba o las rechaza en un plazo de 3 días laborables. En agosto y en Navidad no pueden coincidir de vacaciones más de dos personas del mismo equipo.

## Otros permisos
Por el nacimiento de un hijo o hija hay 16 semanas de permiso retribuido. El día de la mudanza se concede un día libre, y para visitas médicas hasta 20 horas al año sin descontar del salario. Los permisos por fallecimiento de un familiar cercano son de 4 días.
""".trimIndent(),
        ),
        SampleDocument(
            id = "sample-gastos",
            title = "Política de gastos",
            text = """
# Política de gastos

## Viajes
Los viajes de trabajo se reservan a través de la agencia interna, siempre en clase turista. Si el trayecto de tren dura menos de 4 horas no se autoriza el avión. El alojamiento tiene un tope de 120 euros por noche en España y de 160 euros en el extranjero.

## Comidas y dietas
La dieta diaria durante un viaje es de 35 euros, sin necesidad de presentar tickets. Las comidas con clientes se reembolsan con ticket hasta 60 euros por persona y requieren aprobación previa del responsable.

## Cómo reclamar un gasto
Los gastos se envían en la aplicación Rendi dentro de los 30 días siguientes al pago, adjuntando la factura. Los reembolsos se abonan en la nómina del mes siguiente. Un gasto sin factura no se reembolsa, salvo la dieta diaria.
""".trimIndent(),
        ),
        SampleDocument(
            id = "sample-seguridad",
            title = "Normas de seguridad de la información",
            text = """
# Normas de seguridad de la información

## Contraseñas y acceso
Las contraseñas deben tener al menos 14 caracteres y se guardan siempre en el gestor corporativo, nunca en papel ni en el navegador. Se cambian solo si hay sospecha de filtración. El doble factor de autenticación es obligatorio en el correo, la VPN y el repositorio de código.

## Dispositivos
Los portátiles llevan el disco cifrado y se bloquean solos tras 5 minutos de inactividad. Está prohibido conectar memorias USB personales. Si pierdes un dispositivo, avisa al equipo de seguridad en la primera hora escribiendo a seguridad@nimbra.example.

## Incidentes
Un correo sospechoso se reenvía al buzón de phishing y no se abre. Cualquier incidente se notifica en menos de 24 horas; ocultarlo es una falta grave. El equipo de seguridad responde en un máximo de 2 horas laborables.
""".trimIndent(),
        ),
        SampleDocument(
            id = "sample-onboarding",
            title = "Guía de incorporación",
            text = """
# Guía de incorporación

## Tu primera semana
El primer día recibes el portátil y las credenciales en la oficina de Valencia a las 9:30. Durante la primera semana tienes una sesión con tu responsable cada día y una comida de bienvenida con el equipo el viernes. Se te asigna una persona mentora que te acompaña durante tres meses.

## Herramientas
Usamos Slack para la mensajería, Linear para las tareas y Notion para la documentación. El código vive en GitLab y se despliega con una pipeline automática cada vez que se fusiona en la rama principal.

## Periodo de prueba
El periodo de prueba dura 3 meses. Al final se hace una revisión de objetivos con tu responsable y con recursos humanos. El teletrabajo es posible hasta 3 días por semana una vez superado el primer mes.
""".trimIndent(),
        ),
        SampleDocument(
            id = "sample-faq",
            title = "Preguntas frecuentes del producto",
            text = """
# Preguntas frecuentes del producto

## ¿Qué es Nimbra Notas?
Nimbra Notas es nuestra aplicación de notas colaborativas. Permite escribir en equipo en tiempo real y funciona sin conexión, sincronizando los cambios al volver a tener red.

## Planes y precios
El plan Gratis incluye hasta 3 espacios de trabajo y 1 GB de almacenamiento. El plan Pro cuesta 8 euros al mes por persona e incluye espacios ilimitados, 100 GB y el historial de versiones de 90 días. El plan Empresa tiene precio a medida y añade inicio de sesión único.

## Soporte
El soporte por chat está disponible de lunes a viernes de 9:00 a 18:00 hora peninsular. Las cuentas Empresa tienen un gestor dedicado y respuesta en menos de 4 horas. Para recuperar una nota borrada hay un plazo de 30 días en la papelera.
""".trimIndent(),
        ),
    )

    val questions: List<EvalQuestion> = listOf(
        EvalQuestion("q-vac-dias", "¿Cuántos días de vacaciones tengo al año?", "sample-vacaciones", "23 días laborables"),
        EvalQuestion("q-vac-antelacion", "¿Con cuánta antelación tengo que pedir las vacaciones?", "sample-vacaciones", "15 días de antelación"),
        EvalQuestion("q-gastos-hotel", "¿Cuál es el tope de alojamiento por noche en el extranjero?", "sample-gastos", "160 euros en el extranjero"),
        EvalQuestion("q-gastos-app", "¿En qué aplicación se envían los gastos?", "sample-gastos", "aplicación Rendi"),
        EvalQuestion("q-seg-password", "¿Cuántos caracteres mínimos debe tener una contraseña?", "sample-seguridad", "al menos 14 caracteres"),
        EvalQuestion("q-seg-perdida", "¿Qué hago si pierdo el portátil?", "sample-seguridad", "avisa al equipo de seguridad en la primera hora"),
        EvalQuestion("q-onb-teletrabajo", "¿Cuántos días a la semana puedo teletrabajar?", "sample-onboarding", "hasta 3 días por semana"),
        EvalQuestion("q-onb-tareas", "¿Qué herramienta usamos para las tareas?", "sample-onboarding", "Linear para las tareas"),
        EvalQuestion("q-faq-pro", "¿Cuánto cuesta el plan Pro?", "sample-faq", "cuesta 8 euros al mes"),
        EvalQuestion("q-faq-papelera", "¿Cuánto tiempo se conserva una nota borrada en la papelera?", "sample-faq", "plazo de 30 días en la papelera"),
    )

    /** A question the corpus cannot answer: a good RAG answer says so instead of inventing something. */
    const val OUT_OF_SCOPE_QUESTION = "¿Cuál es el horario de la cafetería de la oficina?"

    fun document(id: String): SampleDocument? = documents.firstOrNull { it.id == id }
}
